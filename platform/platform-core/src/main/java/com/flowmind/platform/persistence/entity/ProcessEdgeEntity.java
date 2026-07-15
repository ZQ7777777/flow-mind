package com.flowmind.platform.persistence.entity;

import lombok.Data;

/** 流程连线表 process_edge 的持久化实体。
 *
 * @author Yuxin Xu
 * @since 2026-07-15
 **/
@Data
public class ProcessEdgeEntity {
    /**
     * 连线主键。
     */
    private String id;
    /**
     * 所属流程定义 ID。
     */
    private String definitionId;
    /**
     * 定义内唯一的连线编码。
     */
    private String edgeCode;
    /**
     * 来源节点编码。
     */
    private String sourceNodeCode;
    /**
     * 目标节点编码。
     */
    private String targetNodeCode;
    /**
     * 条件表达式，仅读取流程变量和当前上下文。
     */
    private String conditionExpression;
    /**
     * 是否为来源节点的默认出线。
     */
    private Boolean defaultEdge;
    /**
     * 连线判断或展示顺序。
     */
    private Integer sortOrder;
}
