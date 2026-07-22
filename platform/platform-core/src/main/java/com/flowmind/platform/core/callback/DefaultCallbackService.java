package com.flowmind.platform.core.callback;

import com.flowmind.platform.api.dto.CallbackLogDTO;
import com.flowmind.platform.api.dto.CallbackLogQuery;
import com.flowmind.platform.api.dto.PageResult;
import com.flowmind.platform.api.dto.WorkflowEvent;
import com.flowmind.platform.api.service.CallbackService;
import com.flowmind.platform.core.query.PageQueryNormalizer;
import com.flowmind.platform.persistence.entity.ProcessCallbackLogEntity;
import com.flowmind.platform.persistence.repository.ProcessCallbackLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 默认回调服务。外部处理异常只记录 FAILED，不回滚主流程数据。
 */
@Service
public class DefaultCallbackService implements CallbackService {

    private final ProcessCallbackLogRepository callbackLogRepository;
    private final CallbackLogMapper callbackLogMapper;
    private final CallbackOutboxService callbackOutboxService;

    public DefaultCallbackService(ProcessCallbackLogRepository callbackLogRepository,
                                  CallbackLogMapper callbackLogMapper,
                                  CallbackOutboxService callbackOutboxService) {
        this.callbackLogRepository = callbackLogRepository;
        this.callbackLogMapper = callbackLogMapper;
        this.callbackOutboxService = callbackOutboxService;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void publishCallback(WorkflowEvent event) {
        callbackOutboxService.appendPending(event);
    }

    @Override
    public PageResult<CallbackLogDTO> queryCallbackLogs(CallbackLogQuery query) {
        CallbackLogQuery normalized = query == null ? new CallbackLogQuery() : query;
        int pageNo = PageQueryNormalizer.normalizePageNo(normalized.getPageNo());
        int pageSize = PageQueryNormalizer.normalizePageSize(normalized.getPageSize());
        List<CallbackLogDTO> records = new ArrayList<CallbackLogDTO>();
        for (ProcessCallbackLogEntity entity : callbackLogRepository.query(normalized)) {
            records.add(callbackLogMapper.toDTO(entity));
        }
        long total = callbackLogRepository.count(normalized);
        PageResult<CallbackLogDTO> result = new PageResult<CallbackLogDTO>();
        result.setRecords(records);
        result.setPageNo(Integer.valueOf(pageNo));
        result.setPageSize(Integer.valueOf(pageSize));
        result.setTotal(Long.valueOf(total));
        result.setTotalPages(Integer.valueOf((int) ((total + pageSize - 1) / pageSize)));
        return result;
    }
}
