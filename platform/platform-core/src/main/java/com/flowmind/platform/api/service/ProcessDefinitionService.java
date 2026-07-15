package com.flowmind.platform.api.service;

import com.flowmind.platform.api.dto.PageResult;
import com.flowmind.platform.api.dto.ProcessDefinitionDTO;
import com.flowmind.platform.api.dto.ProcessDefinitionDetailDTO;
import com.flowmind.platform.api.dto.ProcessNodeDTO;
import com.flowmind.platform.api.dto.ValidationResult;
import com.flowmind.platform.api.request.CopyProcessDefinitionRequest;
import com.flowmind.platform.api.request.CreateProcessDefinitionRequest;
import com.flowmind.platform.api.request.DefinitionOperationRequest;
import com.flowmind.platform.api.request.GrayReleaseRequest;
import com.flowmind.platform.api.dto.ProcessDefinitionQuery;
import com.flowmind.platform.api.request.SaveProcessGraphRequest;

import java.util.List;

/**
 * 流程定义管理服务。
 */
public interface ProcessDefinitionService {
    /**
     * 创建流程定义草稿。
     *
     * @param request 创建请求
     * @return 流程定义概要
     */
    ProcessDefinitionDTO createDefinition(CreateProcessDefinitionRequest request);

    /**
     * 保存流程图结构。
     *
     * @param definitionId 流程定义 ID
     * @param request 保存请求
     * @return 流程定义概要
     */
    ProcessDefinitionDTO saveGraph(String definitionId, SaveProcessGraphRequest request);

    /**
     * 发布前校验流程定义。
     *
     * @param definitionId 流程定义 ID
     * @return 校验结果
     */
    ValidationResult validateForPublish(String definitionId);

    /**
     * 发布流程定义。
     *
     * @param request 定义操作请求
     * @return 流程定义概要
     */
    ProcessDefinitionDTO publish(DefinitionOperationRequest request);

    /**
     * 激活流程定义。
     *
     * @param request 定义操作请求
     * @return 流程定义概要
     */
    ProcessDefinitionDTO activate(DefinitionOperationRequest request);

    /**
     * 停用流程定义。
     *
     * @param request 定义操作请求
     * @return 流程定义概要
     */
    ProcessDefinitionDTO deactivate(DefinitionOperationRequest request);

    /**
     * 归档流程定义。
     *
     * @param request 定义操作请求
     * @return 流程定义概要
     */
    ProcessDefinitionDTO archive(DefinitionOperationRequest request);

    /**
     * 开启灰度发布。
     *
     * @param request 灰度发布请求
     * @return 流程定义概要
     */
    ProcessDefinitionDTO enableGray(GrayReleaseRequest request);

    /**
     * 关闭灰度发布。
     *
     * @param request 定义操作请求
     * @return 流程定义概要
     */
    ProcessDefinitionDTO disableGray(DefinitionOperationRequest request);

    /**
     * 复制流程定义为新草稿。
     *
     * @param definitionId 原流程定义 ID
     * @param request 复制请求
     * @return 新流程定义概要
     */
    ProcessDefinitionDTO copyDefinition(String definitionId, CopyProcessDefinitionRequest request);

    /**
     * 删除流程定义及其关联运行数据。
     *
     * @param request 定义操作请求
     */
    void deleteDefinition(DefinitionOperationRequest request);

    /**
     * 查询流程定义详情。
     *
     * @param definitionId 流程定义 ID
     * @return 流程定义详情
     */
    ProcessDefinitionDetailDTO getDefinition(String definitionId);

    /**
     * 分页查询流程定义。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<ProcessDefinitionDTO> searchDefinitions(ProcessDefinitionQuery query);

    /**
     * 查询流程定义节点列表。
     *
     * @param definitionId 流程定义 ID
     * @return 节点列表
     */
    List<ProcessNodeDTO> listNodes(String definitionId);
}
