package com.flowmind.business.workflow;

import com.flowmind.business.workflow.dto.WorkflowActionRequests;
import com.flowmind.business.workflow.dto.WorkflowTaskActionResponse;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

/**
 * 通用流程任务动作接口，统一接收任务版本和幂等键并转交业务服务处理。
 */
@RestController
@RequestMapping("/api/workflow/tasks/{taskId}")
public class WorkflowActionController {
    private final WorkflowActionService actionService;

    public WorkflowActionController(WorkflowActionService actionService) { this.actionService = actionService; }

    /** 审批通过当前任务。 */
    @PostMapping("/approve")
    public WorkflowTaskActionResponse approve(@PathVariable String taskId,
            @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody WorkflowActionRequests.Basic body) {
        return actionService.execute("approve", taskId, key, body);
    }

    /** 提交或发送当前任务，不开放审批阶段变量编辑。 */
    @PostMapping("/submit")
    public WorkflowTaskActionResponse submit(@PathVariable String taskId,
            @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody WorkflowActionRequests.Submit body) {
        return actionService.execute("submit", taskId, key, body);
    }

    /** 将当前任务驳回到请求中指定的已允许节点。 */
    @PostMapping("/reject")
    public WorkflowTaskActionResponse reject(@PathVariable String taskId,
            @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody WorkflowActionRequests.Reject body) {
        return actionService.execute("reject", taskId, key, body);
    }

    /** 将当前任务退回发起人或申请节点。 */
    @PostMapping("/return")
    public WorkflowTaskActionResponse returnTask(@PathVariable String taskId,
            @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody WorkflowActionRequests.Basic body) {
        return actionService.execute("return", taskId, key, body);
    }

    /** 由上一节点办理人撤回当前下游任务。 */
    @PostMapping("/withdraw")
    public WorkflowTaskActionResponse withdraw(@PathVariable String taskId,
            @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody WorkflowActionRequests.Basic body) {
        return actionService.execute("withdraw", taskId, key, body);
    }

    /** 将返工任务直送回平台解析出的可信驳回来源节点。 */
    @PostMapping("/direct-send")
    public WorkflowTaskActionResponse directSend(@PathVariable String taskId,
            @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody WorkflowActionRequests.DirectSend body) {
        return actionService.execute("direct-send", taskId, key, body);
    }

    /** 将当前任务转交给指定用户继续办理。 */
    @PostMapping("/transfer")
    public WorkflowTaskActionResponse transfer(@PathVariable String taskId,
            @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody WorkflowActionRequests.Transfer body) {
        return actionService.execute("transfer", taskId, key, body);
    }

    /** 将当前任务主动委派给指定代理用户办理。 */
    @PostMapping("/delegate")
    public WorkflowTaskActionResponse delegate(@PathVariable String taskId,
            @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody WorkflowActionRequests.Delegate body) {
        return actionService.execute("delegate", taskId, key, body);
    }

    /** 在当前任务节点临时增加办理用户。 */
    @PostMapping("/add-sign")
    public WorkflowTaskActionResponse addSign(@PathVariable String taskId,
            @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody WorkflowActionRequests.AddSign body) {
        return actionService.execute("add-sign", taskId, key, body);
    }

    /** 认领当前候选任务。 */
    @PostMapping("/claim")
    public WorkflowTaskActionResponse claim(@PathVariable String taskId,
            @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody WorkflowActionRequests.Basic body) {
        return actionService.execute("claim", taskId, key, body);
    }

    /** 取消当前用户对任务的认领。 */
    @PostMapping("/unclaim")
    public WorkflowTaskActionResponse unclaim(@PathVariable String taskId,
            @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody WorkflowActionRequests.Basic body) {
        return actionService.execute("unclaim", taskId, key, body);
    }
}
