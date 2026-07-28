package com.flowmind.platform.web;

import com.flowmind.platform.api.dto.AuditLogDTO;
import com.flowmind.platform.api.dto.AuditLogQuery;
import com.flowmind.platform.api.dto.CallbackLogDTO;
import com.flowmind.platform.api.dto.CallbackLogQuery;
import com.flowmind.platform.api.dto.PageResult;
import com.flowmind.platform.api.service.AdminProcessService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** M5 admin query REST endpoints. */
@RestController
@Tag(name = "Platform Admin Query", description = "Audit and callback query APIs")
public class AdminQueryController {

    private final AdminProcessService adminProcessService;

    public AdminQueryController(AdminProcessService adminProcessService) {
        this.adminProcessService = adminProcessService;
    }

    @Operation(summary = "Query audit logs")
    @GetMapping("/api/platform/admin/audit-logs")
    public PageResult<AuditLogDTO> queryAuditLogs(AuditLogQuery query) {
        return adminProcessService.queryAuditLogs(query);
    }

    @Operation(summary = "Query callback logs")
    @GetMapping("/api/platform/admin/callback-logs")
    public PageResult<CallbackLogDTO> queryCallbackLogs(CallbackLogQuery query) {
        return adminProcessService.queryCallbackLogs(query);
    }
}
