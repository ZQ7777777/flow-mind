package com.flowmind.platform.core.callback;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowmind.platform.api.dto.WorkflowEvent;
import com.flowmind.platform.api.spi.WorkflowCallbackHandler;
import com.flowmind.platform.persistence.entity.ProcessCallbackLogEntity;
import com.flowmind.platform.persistence.repository.ProcessCallbackLogRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/** Dispatches pending callback outbox rows and records failures as alerts. */
@Service
public class CallbackDispatchService {

    private final ProcessCallbackLogRepository callbackLogRepository;
    private final WorkflowCallbackHandler callbackHandler;
    private final CallbackFailureAlertService failureAlertService;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    public CallbackDispatchService(ProcessCallbackLogRepository callbackLogRepository,
                                   WorkflowCallbackHandler callbackHandler,
                                   CallbackFailureAlertService failureAlertService) {
        this.callbackLogRepository = callbackLogRepository;
        this.callbackHandler = callbackHandler;
        this.failureAlertService = failureAlertService;
    }

    public int dispatchPending(int limit) {
        List<ProcessCallbackLogEntity> pending = callbackLogRepository.findPending(limit);
        int dispatched = 0;
        for (ProcessCallbackLogEntity log : pending) {
            try {
                WorkflowEvent event = objectMapper.readValue(log.getPayloadJson(), WorkflowEvent.class);
                callbackHandler.handle(event);
                callbackLogRepository.markSuccess(log.getEventId());
                dispatched++;
            } catch (Exception ex) {
                callbackLogRepository.markFailed(log.getEventId(), ex.getMessage());
                ProcessCallbackLogEntity updated = callbackLogRepository.findByEventId(log.getEventId());
                failureAlertService.alert(updated == null ? log : updated, ex.getMessage());
            }
        }
        return dispatched;
    }
}
