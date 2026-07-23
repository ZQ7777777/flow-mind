package com.flowmind.platform.web;

import com.flowmind.platform.api.dto.CallbackLogDTO;
import com.flowmind.platform.api.dto.CallbackLogQuery;
import com.flowmind.platform.api.dto.PageResult;
import com.flowmind.platform.api.dto.WorkflowEvent;
import com.flowmind.platform.api.service.CallbackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 流程回调 REST 适配层。
 *
 * @author FlowMind
 * @since 2026-07-23
 */
@RestController
@Tag(name = "Workflow Callback", description = "Workflow callback APIs")
public class CallbackController {

    private final CallbackService callbackService;

    public CallbackController(CallbackService callbackService) {
        this.callbackService = callbackService;
    }

    @Operation(summary = "Publish workflow callback event")
    @PostMapping("/api/platform/callbacks/events")
    public void publishCallback(@RequestBody WorkflowEvent event) {
        callbackService.publishCallback(event);
    }

    @Operation(summary = "Query callback logs")
    @GetMapping("/api/platform/callbacks/logs")
    public PageResult<CallbackLogDTO> queryCallbackLogs(CallbackLogQuery query) {
        return callbackService.queryCallbackLogs(query);
    }
}
