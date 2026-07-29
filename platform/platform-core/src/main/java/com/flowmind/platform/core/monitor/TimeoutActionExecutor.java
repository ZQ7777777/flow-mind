package com.flowmind.platform.core.monitor;

import com.flowmind.platform.api.request.ForceCompleteRequest;
import com.flowmind.platform.api.request.JumpNodeRequest;
import com.flowmind.platform.api.request.TerminateProcessRequest;
import com.flowmind.platform.api.service.AdminProcessService;
import com.flowmind.platform.api.service.ProcessRuntimeService;
import com.flowmind.platform.persistence.entity.ProcessActiveTaskEntity;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/** Executes timeout automatic actions through existing runtime/admin services. */
@Component
public class TimeoutActionExecutor {

    private static final String DEFAULT_OPERATOR = "system_timeout";

    private final AdminProcessService adminProcessService;
    private final ProcessRuntimeService processRuntimeService;

    public TimeoutActionExecutor(AdminProcessService adminProcessService,
                                 ProcessRuntimeService processRuntimeService) {
        this.adminProcessService = adminProcessService;
        this.processRuntimeService = processRuntimeService;
    }

    public void execute(ProcessActiveTaskEntity task,
                        TimeoutPolicy policy,
                        String operatorUserId,
                        LocalDateTime scanAt) {
        String action = policy.getAction();
        String operator = isBlank(operatorUserId) ? DEFAULT_OPERATOR : operatorUserId;
        String operationId = operationId(task, action, scanAt);
        if (TimeoutPolicy.ACTION_JUMP.equals(action)) {
            JumpNodeRequest request = new JumpNodeRequest();
            request.setOperationId(operationId);
            request.setInstanceId(task.getInstanceId());
            request.setTargetNodeCode(policy.getTargetNodeCode());
            request.setOperatorUserId(operator);
            request.setComment("timeout auto jump from task " + task.getId());
            adminProcessService.jumpToNode(request);
            return;
        }
        if (TimeoutPolicy.ACTION_FORCE_COMPLETE.equals(action)) {
            ForceCompleteRequest request = new ForceCompleteRequest();
            request.setOperationId(operationId);
            request.setInstanceId(task.getInstanceId());
            request.setOperatorUserId(operator);
            request.setComment("timeout auto force complete from task " + task.getId());
            adminProcessService.forceComplete(request);
            return;
        }
        if (TimeoutPolicy.ACTION_TERMINATE.equals(action)) {
            TerminateProcessRequest request = new TerminateProcessRequest();
            request.setOperationId(operationId);
            request.setInstanceId(task.getInstanceId());
            request.setOperatorUserId(operator);
            request.setComment("timeout auto terminate from task " + task.getId());
            processRuntimeService.terminate(request);
        }
    }

    private String operationId(ProcessActiveTaskEntity task, String action, LocalDateTime scanAt) {
        String window = task.getDueAt() == null ? String.valueOf(scanAt) : String.valueOf(task.getDueAt());
        return "timeout:" + task.getId() + ":" + action + ":" + window;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
