package com.flowmind.business.admin;

import com.flowmind.platform.api.dto.OperationResult;
import com.flowmind.platform.api.dto.PageResult;
import com.flowmind.platform.api.dto.ProcessDefinitionDTO;
import com.flowmind.platform.api.dto.ProcessDefinitionDetailDTO;
import com.flowmind.platform.api.dto.ProcessDefinitionQuery;
import com.flowmind.platform.api.dto.ValidationResult;
import com.flowmind.platform.api.request.CopyProcessDefinitionRequest;
import com.flowmind.platform.api.request.CreateProcessDefinitionRequest;
import com.flowmind.platform.api.request.DefinitionOperationRequest;
import com.flowmind.platform.api.request.SaveProcessGraphRequest;
import com.flowmind.platform.api.service.ProcessDefinitionService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Business-side administrator API for process-definition management. */
@RestController
@RequestMapping("/api/admin/process-definitions")
public class AdminProcessDefinitionController {

    private final ProcessDefinitionService definitionService;
    private final AdminAccessGuard accessGuard;

    public AdminProcessDefinitionController(ProcessDefinitionService definitionService,
                                            AdminAccessGuard accessGuard) {
        this.definitionService = definitionService;
        this.accessGuard = accessGuard;
    }

    @GetMapping
    public PageResult<ProcessDefinitionDTO> search(ProcessDefinitionQuery query) {
        accessGuard.requireAdministrator();
        return definitionService.searchDefinitions(query);
    }

    @PostMapping
    public ProcessDefinitionDTO create(@RequestBody CreateProcessDefinitionRequest request) {
        request.setOperatorUserId(accessGuard.requireAdministrator());
        return definitionService.createDefinition(request);
    }

    @GetMapping("/{definitionId}")
    public ProcessDefinitionDetailDTO detail(@PathVariable String definitionId) {
        accessGuard.requireAdministrator();
        return definitionService.getDefinition(definitionId);
    }

    @PutMapping("/{definitionId}/graph")
    public ProcessDefinitionDTO saveGraph(@PathVariable String definitionId,
                                          @RequestBody SaveProcessGraphRequest request) {
        request.setOperatorUserId(accessGuard.requireAdministrator());
        return definitionService.saveGraph(definitionId, request);
    }

    @GetMapping("/{definitionId}/publish-validation")
    public ValidationResult validate(@PathVariable String definitionId) {
        accessGuard.requireAdministrator();
        return definitionService.validateForPublish(definitionId);
    }

    @PostMapping("/{definitionId}/publish")
    public ProcessDefinitionDTO publish(@PathVariable String definitionId,
                                        @RequestBody DefinitionOperationRequest request) {
        trustedOperation(definitionId, request);
        return definitionService.publish(request);
    }

    @PostMapping("/{definitionId}/activate")
    public ProcessDefinitionDTO activate(@PathVariable String definitionId,
                                         @RequestBody DefinitionOperationRequest request) {
        trustedOperation(definitionId, request);
        return definitionService.activate(request);
    }

    @PostMapping("/{definitionId}/deactivate")
    public ProcessDefinitionDTO deactivate(@PathVariable String definitionId,
                                           @RequestBody DefinitionOperationRequest request) {
        trustedOperation(definitionId, request);
        return definitionService.deactivate(request);
    }

    @PostMapping("/{definitionId}/archive")
    public ProcessDefinitionDTO archive(@PathVariable String definitionId,
                                        @RequestBody DefinitionOperationRequest request) {
        trustedOperation(definitionId, request);
        return definitionService.archive(request);
    }

    @PostMapping("/{definitionId}/copy")
    public ProcessDefinitionDTO copy(@PathVariable String definitionId,
                                     @RequestBody CopyProcessDefinitionRequest request) {
        request.setOperatorUserId(accessGuard.requireAdministrator());
        return definitionService.copyDefinition(definitionId, request);
    }

    @DeleteMapping("/{definitionId}")
    public OperationResult delete(@PathVariable String definitionId,
                                  @RequestBody DefinitionOperationRequest request) {
        trustedOperation(definitionId, request);
        return definitionService.deleteDefinition(request);
    }

    private void trustedOperation(String definitionId, DefinitionOperationRequest request) {
        request.setDefinitionId(definitionId);
        request.setOperatorUserId(accessGuard.requireAdministrator());
    }
}
