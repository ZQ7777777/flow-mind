package com.flowmind.platform.api.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 流程定义详情返回对象，在基础定义信息之外包含流程图结构和附件配置。
 *
 * @author Yuxin Xu
 * @since 2026-07-14
 */
@Data
public class ProcessDefinitionDetailDTO extends ProcessDefinitionDTO {
    private static final long serialVersionUID = 1L;

    /**
     * 当前定义版本下的节点列表。
     */
    private List<ProcessNodeDTO> nodes = new ArrayList<>();
    /**
     * 当前定义版本下的连线列表。
     */
    private List<ProcessEdgeDTO> edges = new ArrayList<>();
    /**
     * 当前定义版本下的表单字段列表。
     */
    private List<ProcessFormFieldDTO> formFields = new ArrayList<>();
    /**
     * 当前定义版本绑定的附件模板与附件配置列表。
     */
    private List<ProcessAttachmentTemplateDTO> attachmentTemplates = new ArrayList<>();
}
