package com.flowmind.platform.api.entity.dto;

/**
 * 流程连线定义。
 */
public class ProcessEdgeDTO {

    /** 连线 ID。 */
    private String edgeId;
    /** 流程定义 ID。 */
    private String definitionId;
    /** 连线编码。 */
    private String edgeCode;
    /** 来源节点编码。 */
    private String sourceNodeCode;
    /** 目标节点编码。 */
    private String targetNodeCode;
    /** 条件表达式。 */
    private String conditionExpression;
    /** 是否默认出线。 */
    private Boolean defaultEdge;
    /** 条件判断顺序。 */
    private Integer sortOrder;

    public ProcessEdgeDTO() {
    }

    public String getEdgeId() {
        return edgeId;
    }

    public void setEdgeId(String edgeId) {
        this.edgeId = edgeId;
    }

    public String getDefinitionId() {
        return definitionId;
    }

    public void setDefinitionId(String definitionId) {
        this.definitionId = definitionId;
    }

    public String getEdgeCode() {
        return edgeCode;
    }

    public void setEdgeCode(String edgeCode) {
        this.edgeCode = edgeCode;
    }

    public String getSourceNodeCode() {
        return sourceNodeCode;
    }

    public void setSourceNodeCode(String sourceNodeCode) {
        this.sourceNodeCode = sourceNodeCode;
    }

    public String getTargetNodeCode() {
        return targetNodeCode;
    }

    public void setTargetNodeCode(String targetNodeCode) {
        this.targetNodeCode = targetNodeCode;
    }

    public String getConditionExpression() {
        return conditionExpression;
    }

    public void setConditionExpression(String conditionExpression) {
        this.conditionExpression = conditionExpression;
    }

    public Boolean getDefaultEdge() {
        return defaultEdge;
    }

    public void setDefaultEdge(Boolean defaultEdge) {
        this.defaultEdge = defaultEdge;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }
}
