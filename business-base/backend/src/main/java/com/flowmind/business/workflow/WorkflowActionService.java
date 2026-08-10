package com.flowmind.business.workflow;

import com.flowmind.business.common.OperationIdFactory;
import com.flowmind.business.platform.PlatformDtoMapper;
import com.flowmind.business.platform.PlatformFacade;
import com.flowmind.business.workflow.dto.WorkflowActionRequests;
import com.flowmind.business.workflow.dto.WorkflowTaskActionResponse;
import com.flowmind.platform.api.request.AddSignRequest;
import com.flowmind.platform.api.request.ApproveTaskRequest;
import com.flowmind.platform.api.request.ClaimTaskRequest;
import com.flowmind.platform.api.request.DelegateTaskRequest;
import com.flowmind.platform.api.request.DirectSendRequest;
import com.flowmind.platform.api.request.RejectTaskRequest;
import com.flowmind.platform.api.request.ReturnTaskRequest;
import com.flowmind.platform.api.request.SubmitTaskRequest;
import com.flowmind.platform.api.request.TaskOperationRequest;
import com.flowmind.platform.api.request.TransferTaskRequest;
import com.flowmind.platform.api.request.UnclaimTaskRequest;
import com.flowmind.platform.api.request.WithdrawTaskRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;

/**
 * 通用任务动作服务，负责访问门禁、可信身份、幂等号和平台请求适配。
 */
@Service
public class WorkflowActionService {
    private static final String DOMAIN = "workflow";

    private final PlatformFacade platformFacade;
    private final PlatformDtoMapper mapper;
    private final OperationIdFactory operationIdFactory;
    private final WorkflowQueryService queryService;

    public WorkflowActionService(PlatformFacade platformFacade, PlatformDtoMapper mapper,
                                 OperationIdFactory operationIdFactory, WorkflowQueryService queryService) {
        this.platformFacade = platformFacade;
        this.mapper = mapper;
        this.operationIdFactory = operationIdFactory;
        this.queryService = queryService;
    }

    /**
     * 执行指定通用任务动作。
     * 请求中的用户和任务标识不参与平台调用，统一使用 URL taskId 与服务端可信用户。
     *
     * @param action 通用动作编码
     * @param taskId URL 中的活动任务 ID
     * @param idempotencyKey 客户端重试时复用的幂等键
     * @param input 任务版本、意见及动作特定参数
     * @return 脱离平台内部对象的动作结果
     */
    public WorkflowTaskActionResponse execute(String action, String taskId, String idempotencyKey,
                                               WorkflowActionRequests.Basic input) {
        queryService.authorizedTaskInstance(taskId);
        String userId = platformFacade.currentUser().getUserId();
        TaskOperationRequest request = request(action, input);
        request.setTaskId(taskId);
        request.setExpectedTaskVersion(input.getExpectedTaskVersion());
        request.setOperatorUserId(userId);
        request.setComment(input.getComment());
        request.setOperationId(operationIdFactory.create(DOMAIN, action, taskId, userId, idempotencyKey));
        return mapper.action(platformFacade.execute(action, request));
    }

    /**
     * 将通用动作编码和业务请求转换为对应的平台请求类型。
     * 审批阶段不映射任意流程变量，加签用户列表会保持顺序并去重。
     */
    private TaskOperationRequest request(String action, WorkflowActionRequests.Basic input) {
        if ("approve".equals(action)) return new ApproveTaskRequest();
        if ("submit".equals(action)) return new SubmitTaskRequest();
        if ("return".equals(action)) return new ReturnTaskRequest();
        if ("withdraw".equals(action)) return new WithdrawTaskRequest();
        if ("claim".equals(action)) return new ClaimTaskRequest();
        if ("unclaim".equals(action)) return new UnclaimTaskRequest();
        if ("reject".equals(action)) {
            RejectTaskRequest request = new RejectTaskRequest();
            request.setTargetNodeCode(((WorkflowActionRequests.Reject) input).getTargetNodeCode());
            return request;
        }
        if ("direct-send".equals(action)) {
            DirectSendRequest request = new DirectSendRequest();
            request.setTargetNodeCode(((WorkflowActionRequests.DirectSend) input).getTargetNodeCode());
            return request;
        }
        if ("transfer".equals(action)) {
            TransferTaskRequest request = new TransferTaskRequest();
            request.setTargetUserId(((WorkflowActionRequests.Transfer) input).getTargetUserId());
            return request;
        }
        if ("delegate".equals(action)) {
            WorkflowActionRequests.Delegate source = (WorkflowActionRequests.Delegate) input;
            DelegateTaskRequest request = new DelegateTaskRequest();
            request.setTargetUserId(source.getTargetUserId()); request.setTargetUserName(source.getTargetUserName());
            return request;
        }
        if ("add-sign".equals(action)) {
            AddSignRequest request = new AddSignRequest();
            request.setAddSignUserIds(new ArrayList<String>(new LinkedHashSet<String>(
                    ((WorkflowActionRequests.AddSign) input).getAddSignUserIds())));
            return request;
        }
        throw new IllegalArgumentException("unsupported workflow action");
    }
}
