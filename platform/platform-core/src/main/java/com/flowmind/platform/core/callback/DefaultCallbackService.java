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
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;

/**
 * 默认回调服务。
 *
 * <p>M2 阶段的 publishCallback 只负责在当前业务事务中插入 Outbox 记录，
 * 即写入 process_callback_log(PENDING)。它不同步调用外部回调处理器，
 * 也不使用独立事务更新 SUCCESS/FAILED。</p>
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

    /**
     * 发布回调事件到 Outbox。
     *
     * <p>该方法必须在调用方已有事务中执行，只插入 PENDING 回调日志。
     * 外部投递、成功标记和失败标记由后续扫描器或投递组件处理。</p>
     */
    @Override
    public void publishCallback(WorkflowEvent event) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("publishCallback must be called inside an existing transaction");
        }
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
