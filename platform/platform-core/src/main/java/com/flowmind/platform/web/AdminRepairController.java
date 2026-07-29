package com.flowmind.platform.web;

import com.flowmind.platform.api.dto.ProcessInstanceDTO;
import com.flowmind.platform.api.dto.TaskActionResult;
import com.flowmind.platform.api.request.ForceCompleteRequest;
import com.flowmind.platform.api.request.JumpNodeRequest;
import com.flowmind.platform.api.service.AdminProcessService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Admin repair REST endpoints. */
@RestController
@Tag(name = "Platform Admin Repair", description = "Admin repair APIs")
public class AdminRepairController {

    private final AdminProcessService adminProcessService;

    public AdminRepairController(AdminProcessService adminProcessService) {
        this.adminProcessService = adminProcessService;
    }

    @Operation(summary = "Jump instance to node")
    @PostMapping("/api/platform/admin/instances/{instanceId}/jump")
    public TaskActionResult jumpToNode(@Parameter(description = "Instance id") @PathVariable String instanceId,
                                       @RequestBody JumpNodeRequest request) {
        request.setInstanceId(instanceId);
        return adminProcessService.jumpToNode(request);
    }

    @Operation(summary = "Force complete instance")
    @PostMapping("/api/platform/admin/instances/{instanceId}/force-complete")
    public ProcessInstanceDTO forceComplete(@Parameter(description = "Instance id") @PathVariable String instanceId,
                                            @RequestBody ForceCompleteRequest request) {
        request.setInstanceId(instanceId);
        return adminProcessService.forceComplete(request);
    }
}
