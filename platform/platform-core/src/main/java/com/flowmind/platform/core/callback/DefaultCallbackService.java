package com.flowmind.platform.core.callback;

import com.flowmind.platform.api.dto.CallbackLogDTO;
import com.flowmind.platform.api.dto.CallbackLogQuery;
import com.flowmind.platform.api.dto.PageResult;
import com.flowmind.platform.api.dto.WorkflowEvent;
import com.flowmind.platform.api.service.CallbackService;
import com.flowmind.platform.api.spi.WorkflowCallbackHandler;
import com.flowmind.platform.core.query.PageQueryNormalizer;
import com.flowmind.platform.persistence.entity.ProcessCallbackLogEntity;
import com.flowmind.platform.persistence.repository.ProcessCallbackLogRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 默认回调服务。外部处理异常只记录 FAILED，不回滚主流程数据。
 */
@Service
public class DefaultCallbackService implements CallbackService {

    private final ProcessCallbackLogRepository callbackLogRepository;
    private final CallbackLogMapper callbackLogMapper;
    private final List<WorkflowCallbackHandler> callbackHandlers;

    public DefaultCallbackService(ProcessCallbackLogRepository callbackLogRepository,
                                  CallbackLogMapper callbackLogMapper,
                                  List<WorkflowCallbackHandler> callbackHandlers) {
        this.callbackLogRepository = callbackLogRepository;
        this.callbackLogMapper = callbackLogMapper;
        this.callbackHandlers = callbackHandlers == null
                ? new ArrayList<WorkflowCallbackHandler>() : callbackHandlers;
    }

    @Override
    public void publishCallback(WorkflowEvent event) {
        if (event == null || event.getEventId() == null || event.getEventId().trim().isEmpty()) {
            throw new IllegalArgumentException("eventId must not be empty");
        }
        try {
            for (WorkflowCallbackHandler handler : callbackHandlers) {
                handler.handle(event);
            }
            callbackLogRepository.markSuccess(event.getEventId());
        } catch (RuntimeException ex) {
            callbackLogRepository.markFailed(event.getEventId(), truncate(ex.getMessage()));
        }
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

    private String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }
}
