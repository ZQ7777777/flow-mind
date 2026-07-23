package com.flowmind.platform.web;

import com.flowmind.platform.api.dto.OperationResult;
import com.flowmind.platform.api.dto.PageResult;
import com.flowmind.platform.api.dto.ProcessDefinitionDTO;
import com.flowmind.platform.api.dto.ProcessDefinitionDetailDTO;
import com.flowmind.platform.api.dto.ProcessDefinitionQuery;
import com.flowmind.platform.api.dto.ProcessNodeDTO;
import com.flowmind.platform.api.dto.ValidationResult;
import com.flowmind.platform.api.request.CopyProcessDefinitionRequest;
import com.flowmind.platform.api.request.CreateProcessDefinitionRequest;
import com.flowmind.platform.api.request.DefinitionOperationRequest;
import com.flowmind.platform.api.request.GrayReleaseRequest;
import com.flowmind.platform.api.request.SaveProcessGraphRequest;
import com.flowmind.platform.api.service.ProcessDefinitionService;
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

import java.util.List;

/**
 * 流程定义管理 REST 适配层。
 *
 * @author FlowMind
 * @since 2026-07-23
 */
@RestController
@Tag(name = "Process Definition", description = "Process definition management APIs")
public class ProcessDefinitionController {

    private final ProcessDefinitionService definitionService;

    public ProcessDefinitionController(ProcessDefinitionService definitionService) {
        this.definitionService = definitionService;
    }

    @Operation(summary = "Create process definition")
    @PostMapping("/api/platform/definitions")
    public ProcessDefinitionDTO createDefinition(@RequestBody CreateProcessDefinitionRequest request) {
        return definitionService.createDefinition(request);
    }

    @Operation(summary = "Save process graph")
    @PutMapping("/api/platform/definitions/{definitionId}/graph")
    public ProcessDefinitionDTO saveGraph(@Parameter(description = "Definition id") @PathVariable String definitionId,
                                          @RequestBody SaveProcessGraphRequest request) {
        return definitionService.saveGraph(definitionId, request);
    }

    @Operation(summary = "Validate definition before publishing")
    @GetMapping("/api/platform/definitions/{definitionId}/publish-validation")
    public ValidationResult validateForPublish(
            @Parameter(description = "Definition id") @PathVariable String definitionId) {
        return definitionService.validateForPublish(definitionId);
    }

    @Operation(summary = "Publish process definition")
    @PostMapping("/api/platform/definitions/publish")
    public ProcessDefinitionDTO publish(@RequestBody DefinitionOperationRequest request) {
        return definitionService.publish(request);
    }

    @Operation(summary = "Activate process definition")
    @PostMapping("/api/platform/definitions/activate")
    public ProcessDefinitionDTO activate(@RequestBody DefinitionOperationRequest request) {
        return definitionService.activate(request);
    }

    @Operation(summary = "Deactivate process definition")
    @PostMapping("/api/platform/definitions/deactivate")
    public ProcessDefinitionDTO deactivate(@RequestBody DefinitionOperationRequest request) {
        return definitionService.deactivate(request);
    }

    @Operation(summary = "Archive process definition")
    @PostMapping("/api/platform/definitions/archive")
    public ProcessDefinitionDTO archive(@RequestBody DefinitionOperationRequest request) {
        return definitionService.archive(request);
    }

    @Operation(summary = "Enable gray release")
    @PostMapping("/api/platform/definitions/gray/enable")
    public ProcessDefinitionDTO enableGray(@RequestBody GrayReleaseRequest request) {
        return definitionService.enableGray(request);
    }

    @Operation(summary = "Disable gray release")
    @PostMapping("/api/platform/definitions/gray/disable")
    public ProcessDefinitionDTO disableGray(@RequestBody DefinitionOperationRequest request) {
        return definitionService.disableGray(request);
    }

    @Operation(summary = "Copy process definition")
    @PostMapping("/api/platform/definitions/{definitionId}/copy")
    public ProcessDefinitionDTO copyDefinition(
            @Parameter(description = "Definition id") @PathVariable String definitionId,
            @RequestBody CopyProcessDefinitionRequest request) {
        return definitionService.copyDefinition(definitionId, request);
    }

    @Operation(summary = "Delete process definition")
    @DeleteMapping("/api/platform/definitions")
    public OperationResult deleteDefinition(@RequestBody DefinitionOperationRequest request) {
        return definitionService.deleteDefinition(request);
    }

    @Operation(summary = "Get process definition detail")
    @GetMapping("/api/platform/definitions/{definitionId}")
    public ProcessDefinitionDetailDTO getDefinition(
            @Parameter(description = "Definition id") @PathVariable String definitionId) {
        return definitionService.getDefinition(definitionId);
    }

    @Operation(summary = "Search process definitions")
    @GetMapping("/api/platform/definitions")
    public PageResult<ProcessDefinitionDTO> searchDefinitions(ProcessDefinitionQuery query) {
        return definitionService.searchDefinitions(query);
    }

    @Operation(summary = "List definition nodes")
    @GetMapping("/api/platform/definitions/{definitionId}/nodes")
    public List<ProcessNodeDTO> listNodes(@Parameter(description = "Definition id") @PathVariable String definitionId) {
        return definitionService.listNodes(definitionId);
    }
}
