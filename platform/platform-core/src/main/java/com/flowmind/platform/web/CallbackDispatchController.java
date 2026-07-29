package com.flowmind.platform.web;

import com.flowmind.platform.api.dto.CallbackDispatchResult;
import com.flowmind.platform.api.request.CallbackDispatchRequest;
import com.flowmind.platform.core.callback.CallbackDispatchService;
import com.flowmind.platform.core.runtime.AdminPermissionGuard;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Admin callback dispatch debug endpoint. */
@RestController
@Tag(name = "Platform Callback Dispatch", description = "Admin callback dispatch APIs")
public class CallbackDispatchController {

    private final CallbackDispatchService callbackDispatchService;
    private final AdminPermissionGuard adminPermissionGuard;

    public CallbackDispatchController(CallbackDispatchService callbackDispatchService,
                                      AdminPermissionGuard adminPermissionGuard) {
        this.callbackDispatchService = callbackDispatchService;
        this.adminPermissionGuard = adminPermissionGuard;
    }

    @Operation(summary = "Dispatch pending callbacks")
    @PostMapping("/api/platform/admin/callback-dispatch")
    public CallbackDispatchResult dispatchPending(@RequestBody CallbackDispatchRequest request) {
        adminPermissionGuard.assertAdminUserId(request == null ? null : request.getOperatorUserId());
        int limit = request == null || request.getLimit() == null ? 20 : Math.max(1, request.getLimit().intValue());
        CallbackDispatchResult result = new CallbackDispatchResult();
        result.setDispatchedCount(Integer.valueOf(callbackDispatchService.dispatchPending(limit)));
        return result;
    }
}
