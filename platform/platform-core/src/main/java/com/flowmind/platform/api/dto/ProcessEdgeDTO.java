package com.flowmind.platform.api.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 流程连线数据传输对象，描述节点之间的流转关系和条件网关出线规则。
 *
 * @author Yuxin Xu
 * @since 2026-07-14
 */
@Data
public class ProcessEdgeDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 连线主键，持久化后由平台生成，新增草稿连线可为空。
     */
    private String id;
    /**
     * 所属流程定义 ID，保存流程图时由平台绑定。
     */
    private String definitionId;
    /**
     * 连线编码，同一流程定义内唯一，供模型校验和运行时路径选择使用。
     */
    private String edgeCode;
    /**
     * 来源节点编码，必须引用同一流程定义内存在的节点。
     */
    private String sourceNodeCode;
    /**
     * 目标节点编码，必须引用同一流程定义内存在的节点。
     */
    private String targetNodeCode;
    /**
     * 条件表达式，仅允许配置在排他网关非默认出线上；M5 支持单变量简单比较，
     * 如 amount > 100000、accountType == "company"、urgent == true。
     */
    private String conditionExpression;
    /**
     * 是否为默认出线，排他网关最多只能有一条默认出线，默认出线不配置条件表达式。
     */
    private Boolean defaultEdge;
    /**
     * 条件判断或展示顺序，数值越小越优先。
     */
    private Integer sortOrder;
}
