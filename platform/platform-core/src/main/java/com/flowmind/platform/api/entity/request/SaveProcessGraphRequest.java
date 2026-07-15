package com.flowmind.platform.api.entity.request;

import com.flowmind.platform.api.entity.dto.ProcessAttachmentConfigDTO;
import com.flowmind.platform.api.entity.dto.ProcessEdgeDTO;
import com.flowmind.platform.api.entity.dto.ProcessFormFieldDTO;
import com.flowmind.platform.api.entity.dto.ProcessNodeDTO;

import java.util.List;

/**
 * 保存流程图请求。
 */
public class SaveProcessGraphRequest extends OperationRequest {

    /** 操作人用户 ID。 */
    private String operatorUserId;
    /** 流程节点列表。 */
    private List<ProcessNodeDTO> nodes;
    /** 流程连线列表。 */
    private List<ProcessEdgeDTO> edges;
    /** 表单字段列表。 */
    private List<ProcessFormFieldDTO> formFields;
    /** 附件配置列表。 */
    private List<ProcessAttachmentConfigDTO> attachmentConfigs;

    public SaveProcessGraphRequest() {
    }

    public String getOperatorUserId() {
        return operatorUserId;
    }

    public void setOperatorUserId(String operatorUserId) {
        this.operatorUserId = operatorUserId;
    }

    public List<ProcessNodeDTO> getNodes() {
        return nodes;
    }

    public void setNodes(List<ProcessNodeDTO> nodes) {
        this.nodes = nodes;
    }

    public List<ProcessEdgeDTO> getEdges() {
        return edges;
    }

    public void setEdges(List<ProcessEdgeDTO> edges) {
        this.edges = edges;
    }

    public List<ProcessFormFieldDTO> getFormFields() {
        return formFields;
    }

    public void setFormFields(List<ProcessFormFieldDTO> formFields) {
        this.formFields = formFields;
    }

    public List<ProcessAttachmentConfigDTO> getAttachmentConfigs() {
        return attachmentConfigs;
    }

    public void setAttachmentConfigs(List<ProcessAttachmentConfigDTO> attachmentConfigs) {
        this.attachmentConfigs = attachmentConfigs;
    }
}
