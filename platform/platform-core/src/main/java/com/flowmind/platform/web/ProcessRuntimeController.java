package com.flowmind.platform.web;

import com.flowmind.platform.api.dto.OperationResult;
import com.flowmind.platform.api.dto.ProcessInstanceDTO;
import com.flowmind.platform.api.dto.ProcessInstanceDetailDTO;
import com.flowmind.platform.api.dto.TaskActionResult;
import com.flowmind.platform.api.request.AddSignRequest;
import com.flowmind.platform.api.request.ApproveTaskRequest;
import com.flowmind.platform.api.request.ClaimTaskRequest;
import com.flowmind.platform.api.request.DeleteProcessInstanceRequest;
import com.flowmind.platform.api.request.DirectSendRequest;
import com.flowmind.platform.api.request.RejectTaskRequest;
import com.flowmind.platform.api.request.ReturnTaskRequest;
import com.flowmind.platform.api.request.StartProcessRequest;
import com.flowmind.platform.api.request.SubmitTaskRequest;
import com.flowmind.platform.api.request.TerminateProcessRequest;
import com.flowmind.platform.api.request.TransferTaskRequest;
import com.flowmind.platform.api.request.UnclaimTaskRequest;
import com.flowmind.platform.api.request.UpdateVariablesRequest;
import com.flowmind.platform.api.request.WithdrawTaskRequest;
import com.flowmind.platform.api.service.ProcessRuntimeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 流程运行时 REST 适配层。
 *
 * @author FlowMind
 * @since 2026-07-23
 */
@RestController
@Tag(name = "Process Runtime", description = "Process runtime operation APIs")
public class ProcessRuntimeController {

    private final ProcessRuntimeService runtimeService;

    public ProcessRuntimeController(ProcessRuntimeService runtimeService) {
        this.runtimeService = runtimeService;
    }

    @Operation(summary = "Start process instance")
    @PostMapping("/api/platform/runtime/instances/start")
    public ProcessInstanceDTO startProcess(@RequestBody StartProcessRequest request) {
        return runtimeService.startProcess(request);
    }

    @Operation(summary = "Start process instance, submit starter task and advance")
    @PostMapping("/api/platform/runtime/instances/start-submit")
    public ProcessInstanceDTO startAndSubmit(@RequestBody StartProcessRequest request) {
        return runtimeService.startAndSubmit(request);
    }

    @Operation(summary = "Submit task")
    @PostMapping("/api/platform/runtime/tasks/submit")
    public TaskActionResult submitTask(@RequestBody SubmitTaskRequest request) {
        return runtimeService.submitTask(request);
    }

    @Operation(summary = "Approve task")
    @PostMapping("/api/platform/runtime/tasks/approve")
    public TaskActionResult approve(@RequestBody ApproveTaskRequest request) {
        return runtimeService.approve(request);
    }

    @Operation(summary = "Reject task")
    @PostMapping("/api/platform/runtime/tasks/reject")
    public TaskActionResult reject(@RequestBody RejectTaskRequest request) {
        return runtimeService.reject(request);
    }

    @Operation(summary = "Return task to starter")
    @PostMapping("/api/platform/runtime/tasks/return")
    public TaskActionResult returnToStarter(@RequestBody ReturnTaskRequest request) {
        return runtimeService.returnToStarter(request);
    }

    @Operation(summary = "Withdraw task")
    @PostMapping("/api/platform/runtime/tasks/withdraw")
    public TaskActionResult withdraw(@RequestBody WithdrawTaskRequest request) {
        return runtimeService.withdraw(request);
    }

    @Operation(summary = "Direct send task")
    @PostMapping("/api/platform/runtime/tasks/direct-send")
    public TaskActionResult directSend(@RequestBody DirectSendRequest request) {
        return runtimeService.directSend(request);
    }

    @Operation(summary = "Transfer task")
    @PostMapping("/api/platform/runtime/tasks/transfer")
    public TaskActionResult transfer(@RequestBody TransferTaskRequest request) {
        return runtimeService.transfer(request);
    }

    @Operation(summary = "Add sign task")
    @PostMapping("/api/platform/runtime/tasks/add-sign")
    public TaskActionResult addSign(@RequestBody AddSignRequest request) {
        return runtimeService.addSign(request);
    }

    @Operation(summary = "Claim task")
    @PostMapping("/api/platform/runtime/tasks/claim")
    public TaskActionResult claim(@RequestBody ClaimTaskRequest request) {
        return runtimeService.claim(request);
    }

    @Operation(summary = "Unclaim task")
    @PostMapping("/api/platform/runtime/tasks/unclaim")
    public TaskActionResult unclaim(@RequestBody UnclaimTaskRequest request) {
        return runtimeService.unclaim(request);
    }

    @Operation(summary = "Terminate process instance")
    @PostMapping("/api/platform/runtime/instances/terminate")
    public ProcessInstanceDTO terminate(@RequestBody TerminateProcessRequest request) {
        return runtimeService.terminate(request);
    }

    @Operation(summary = "Delete process instance")
    @DeleteMapping("/api/platform/runtime/instances")
    public OperationResult deleteInstance(@RequestBody DeleteProcessInstanceRequest request) {
        return runtimeService.deleteInstance(request);
    }

    @Operation(summary = "Update instance variables")
    @PutMapping("/api/platform/runtime/instances/variables")
    public ProcessInstanceDTO updateVariables(@RequestBody UpdateVariablesRequest request) {
        return runtimeService.updateVariables(request);
    }

    @Operation(summary = "Get process instance detail")
    @GetMapping("/api/platform/runtime/instances/{instanceId}")
    public ProcessInstanceDetailDTO getInstance(@Parameter(description = "Instance id") @PathVariable String instanceId) {
        return runtimeService.getInstance(instanceId);
    }
}
