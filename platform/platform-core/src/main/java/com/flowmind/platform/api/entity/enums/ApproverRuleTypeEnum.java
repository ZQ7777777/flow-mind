package com.flowmind.platform.api.entity.enums;

/**
 * 审批人解析规则类型。
 */
public enum ApproverRuleTypeEnum {
    /** 指定用户。 */
    USER,
    /** 流程发起人。 */
    STARTER,
    /** 指定部门。 */
    DEPARTMENT,
    /** 指定角色。 */
    ROLE,
    /** 指定部门内的指定角色。 */
    ROLE_IN_DEPARTMENT,
    /** 审批人表达式。 */
    APPROVER_EXPRESSION
}
