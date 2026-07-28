package com.flowmind.platform.core.query;

import com.flowmind.platform.api.dto.PageResult;
import com.flowmind.platform.api.dto.ReadRecordDTO;
import com.flowmind.platform.api.dto.ReadRecordQuery;
import com.flowmind.platform.api.dto.UserContext;
import com.flowmind.platform.api.spi.CurrentUserProvider;
import com.flowmind.platform.core.runtime.RuntimeErrorCodes;
import com.flowmind.platform.core.runtime.RuntimeValidationException;
import com.flowmind.platform.persistence.entity.ProcessReadRecordEntity;
import com.flowmind.platform.persistence.repository.ProcessReadRecordRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Handles idempotent read-record writes and read-record queries. */
@Component
public class ReadRecordManager {

    private final ProcessReadRecordRepository readRecordRepository;
    private final CurrentUserProvider currentUserProvider;

    public ReadRecordManager(ProcessReadRecordRepository readRecordRepository,
                             CurrentUserProvider currentUserProvider) {
        this.readRecordRepository = readRecordRepository;
        this.currentUserProvider = currentUserProvider;
    }

    public ReadRecordDTO markRead(String instanceId) {
        if (isBlank(instanceId)) {
            throw new RuntimeValidationException(RuntimeErrorCodes.INVALID_ACTION, "instanceId is required");
        }
        UserContext user = currentUser();
        return toDTO(readRecordRepository.upsert(instanceId, user.getUserId(), user.getUserName(), LocalDateTime.now()));
    }

    public PageResult<ReadRecordDTO> query(ReadRecordQuery query) {
        ReadRecordQuery normalized = query == null ? new ReadRecordQuery() : query;
        int pageNo = PageQueryNormalizer.normalizePageNo(normalized.getPageNo());
        int pageSize = PageQueryNormalizer.normalizePageSize(normalized.getPageSize());
        List<ReadRecordDTO> records = new ArrayList<ReadRecordDTO>();
        for (ProcessReadRecordEntity entity : readRecordRepository.query(normalized)) {
            records.add(toDTO(entity));
        }
        long total = readRecordRepository.count(normalized);
        PageResult<ReadRecordDTO> result = new PageResult<ReadRecordDTO>();
        result.setRecords(records);
        result.setPageNo(Integer.valueOf(pageNo));
        result.setPageSize(Integer.valueOf(pageSize));
        result.setTotal(Long.valueOf(total));
        result.setTotalPages(Integer.valueOf((int) ((total + pageSize - 1) / pageSize)));
        return result;
    }

    private ReadRecordDTO toDTO(ProcessReadRecordEntity entity) {
        ReadRecordDTO dto = new ReadRecordDTO();
        dto.setReadRecordId(entity.getId());
        dto.setInstanceId(entity.getInstanceId());
        dto.setUserId(entity.getUserId());
        dto.setUserName(entity.getUserName());
        dto.setReadAt(entity.getReadAt());
        return dto;
    }

    private UserContext currentUser() {
        UserContext user = currentUserProvider == null ? null : currentUserProvider.getCurrentUser();
        if (user == null || isBlank(user.getUserId())) {
            throw new RuntimeValidationException(RuntimeErrorCodes.INVALID_ACTION, "current user is required");
        }
        return user;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
