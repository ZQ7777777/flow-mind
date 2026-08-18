package com.flowmind.business.admin;

import com.flowmind.business.common.BusinessApiException;
import com.flowmind.business.entry.BusinessEntryConfigService;
import com.flowmind.business.entry.dto.BusinessEntryConfigResponse;
import com.flowmind.business.entry.dto.BusinessEntryConfigWriteRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理员业务入口配置接口。
 *
 * @author FlowMind
 * @since 2026-08-18
 */
@RestController
@RequestMapping("/api/admin/business-entry-configs")
public class AdminBusinessEntryConfigController {

    private final BusinessEntryConfigService service;
    private final AdminAccessGuard accessGuard;

    public AdminBusinessEntryConfigController(BusinessEntryConfigService service, AdminAccessGuard accessGuard) {
        this.service = service;
        this.accessGuard = accessGuard;
    }

    @GetMapping
    public List<BusinessEntryConfigResponse> list() {
        accessGuard.requireAdministrator();
        return service.listAdminConfigs();
    }

    @GetMapping("/by-definition/{definitionId}")
    public BusinessEntryConfigResponse getByDefinition(@PathVariable String definitionId) {
        accessGuard.requireAdministrator();
        for (BusinessEntryConfigResponse response : service.listAdminConfigs()) {
            if (definitionId.equals(response.getDefinitionId())) {
                return response;
            }
        }
        throw new BusinessApiException(HttpStatus.NOT_FOUND, "BUSINESS_ENTRY_CONFIG_NOT_FOUND",
                "business entry config not found");
    }

    @PostMapping
    public BusinessEntryConfigResponse create(@RequestBody BusinessEntryConfigWriteRequest request) {
        return service.create(request, accessGuard.requireAdministrator());
    }

    @PutMapping("/{id}")
    public BusinessEntryConfigResponse update(@PathVariable String id,
                                              @RequestBody BusinessEntryConfigWriteRequest request) {
        return service.update(id, request, accessGuard.requireAdministrator());
    }

    @PutMapping("/by-definition/{definitionId}")
    public BusinessEntryConfigResponse upsertByDefinition(@PathVariable String definitionId,
                                                          @RequestBody BusinessEntryConfigWriteRequest request) {
        return service.upsertByDefinition(definitionId, request, accessGuard.requireAdministrator());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        accessGuard.requireAdministrator();
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
