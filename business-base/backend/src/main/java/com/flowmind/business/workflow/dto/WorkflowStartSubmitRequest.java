package com.flowmind.business.workflow.dto;

import lombok.Data;

import java.util.Map;

/**
 * 通用发起提交的 JSON payload 部分。
 *
 * @author FlowMind
 * @since 2026-08-18
 */
@Data
public class WorkflowStartSubmitRequest {
    /** 可选外部业务键；用户身份、部门和流程编码不得放入请求体。 */
    private String businessKey;
    /** 按流程定义字段编码组织的业务变量。 */
    private Map<String, Object> variables;
}
