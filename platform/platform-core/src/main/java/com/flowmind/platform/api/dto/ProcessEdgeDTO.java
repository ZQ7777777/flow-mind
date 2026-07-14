package com.flowmind.platform.api.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 流程连线返回对象，描述节点之间的流转关系和条件网关出线规则。
 *
 * @author Yuxin Xu
 * @since 2026-07-14
 */
@Data
public class ProcessEdgeDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 连线主键。
     */
    private String id;
    /**
     * 所属流程定义 ID。
     */
    private String definitionId;
    /**
     * 连线编码，同一定义内唯一。
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
     * 条件表达式，主要用于排他网关非默认出线。
     */
    private String conditionExpression;
    /**
     * 是否为默认出线。
     */
    private Boolean defaultEdge;
    /**
     * 条件判断或展示顺序。
     */
    private Integer sortOrder;

}
