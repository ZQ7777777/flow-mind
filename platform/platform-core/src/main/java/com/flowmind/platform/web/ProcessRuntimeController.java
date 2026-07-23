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
public class ProcessRuntimeController {

    private final ProcessRuntimeService runtimeService;

    public ProcessRuntimeController(ProcessRuntimeService runtimeService) {
        this.runtimeService = runtimeService;
    }

    @PostMapping("/api/platform/runtime/instances/start")
    public ProcessInstanceDTO startProcess(@RequestBody StartProcessRequest request) {
        return runtimeService.startProcess(request);
    }

    @PostMapping("/api/platform/runtime/instances/start-submit")
    public ProcessInstanceDTO startAndSubmit(@RequestBody StartProcessRequest request) {
        return runtimeService.startAndSubmit(request);
    }

    @PostMapping("/api/platform/runtime/tasks/submit")
    public TaskActionResult submitTask(@RequestBody SubmitTaskRequest request) {
        return runtimeService.submitTask(request);
    }

    @PostMapping("/api/platform/runtime/tasks/approve")
    public TaskActionResult approve(@RequestBody ApproveTaskRequest request) {
        return runtimeService.approve(request);
    }

    @PostMapping("/api/platform/runtime/tasks/reject")
    public TaskActionResult reject(@RequestBody RejectTaskRequest request) {
        return runtimeService.reject(request);
    }

    @PostMapping("/api/platform/runtime/tasks/return")
    public TaskActionResult returnToStarter(@RequestBody ReturnTaskRequest request) {
        return runtimeService.returnToStarter(request);
    }

    @PostMapping("/api/platform/runtime/tasks/withdraw")
    public TaskActionResult withdraw(@RequestBody WithdrawTaskRequest request) {
        return runtimeService.withdraw(request);
    }

    @PostMapping("/api/platform/runtime/tasks/direct-send")
    public TaskActionResult directSend(@RequestBody DirectSendRequest request) {
        return runtimeService.directSend(request);
    }

    @PostMapping("/api/platform/runtime/tasks/transfer")
    public TaskActionResult transfer(@RequestBody TransferTaskRequest request) {
        return runtimeService.transfer(request);
    }

    @PostMapping("/api/platform/runtime/tasks/add-sign")
    public TaskActionResult addSign(@RequestBody AddSignRequest request) {
        return runtimeService.addSign(request);
    }

    @PostMapping("/api/platform/runtime/tasks/claim")
    public TaskActionResult claim(@RequestBody ClaimTaskRequest request) {
        return runtimeService.claim(request);
    }

    @PostMapping("/api/platform/runtime/tasks/unclaim")
    public TaskActionResult unclaim(@RequestBody UnclaimTaskRequest request) {
        return runtimeService.unclaim(request);
    }

    @PostMapping("/api/platform/runtime/instances/terminate")
    public ProcessInstanceDTO terminate(@RequestBody TerminateProcessRequest request) {
        return runtimeService.terminate(request);
    }

    @DeleteMapping("/api/platform/runtime/instances")
    public OperationResult deleteInstance(@RequestBody DeleteProcessInstanceRequest request) {
        return runtimeService.deleteInstance(request);
    }

    @PutMapping("/api/platform/runtime/instances/variables")
    public ProcessInstanceDTO updateVariables(@RequestBody UpdateVariablesRequest request) {
        return runtimeService.updateVariables(request);
    }

    @GetMapping("/api/platform/runtime/instances/{instanceId}")
    public ProcessInstanceDetailDTO getInstance(@PathVariable String instanceId) {
        return runtimeService.getInstance(instanceId);
    }
}
