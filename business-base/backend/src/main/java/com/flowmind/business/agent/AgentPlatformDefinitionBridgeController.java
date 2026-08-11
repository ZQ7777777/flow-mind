package com.flowmind.business.agent;

import com.flowmind.platform.api.dto.PageResult;
import com.flowmind.platform.api.dto.ProcessDefinitionDTO;
import com.flowmind.platform.api.dto.ProcessDefinitionDetailDTO;
import com.flowmind.platform.api.dto.ProcessDefinitionQuery;
import com.flowmind.platform.api.dto.ValidationResult;
import com.flowmind.platform.api.request.CreateProcessDefinitionRequest;
import com.flowmind.platform.api.request.DefinitionOperationRequest;
import com.flowmind.platform.api.request.SaveProcessGraphRequest;
import com.flowmind.platform.api.service.ProcessDefinitionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * agent-web 写入 business-base 内嵌 Platform 流程定义库的受控桥接入口。
 * 本类不生成流程定义；对话、需求组装和激活决策归属 agent-web。
 */
@RestController
@RequestMapping("/api/platform/definitions")
public class AgentPlatformDefinitionBridgeController {

    private final ProcessDefinitionService definitionService;

    public AgentPlatformDefinitionBridgeController(ProcessDefinitionService definitionService) {
        this.definitionService = definitionService;
    }

    @PostMapping
    public ProcessDefinitionDTO createDefinition(@RequestBody CreateProcessDefinitionRequest request) {
        return definitionService.createDefinition(request);
    }

    @PutMapping("/{definitionId}/graph")
    public ProcessDefinitionDTO saveGraph(@PathVariable String definitionId,
                                          @RequestBody SaveProcessGraphRequest request) {
        return definitionService.saveGraph(definitionId, request);
    }

    @GetMapping("/{definitionId}/publish-validation")
    public ValidationResult validateForPublish(@PathVariable String definitionId) {
        return definitionService.validateForPublish(definitionId);
    }

    @PostMapping("/publish")
    public ProcessDefinitionDTO publish(@RequestBody DefinitionOperationRequest request) {
        return definitionService.publish(request);
    }

    @PostMapping("/activate")
    public ProcessDefinitionDTO activate(@RequestBody DefinitionOperationRequest request) {
        return definitionService.activate(request);
    }

    @GetMapping("/{definitionId}")
    public ProcessDefinitionDetailDTO getDefinition(@PathVariable String definitionId) {
        return definitionService.getDefinition(definitionId);
    }

    @GetMapping
    public PageResult<ProcessDefinitionDTO> searchDefinitions(ProcessDefinitionQuery query) {
        return definitionService.searchDefinitions(query);
    }
}
