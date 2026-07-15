package com.flowmind.platform.persistence.entity;
import lombok.Data;
import java.time.LocalDateTime;
/** 流程实例表 process_instance 的持久化实体。
 *
 * @author Yuxin Xu
 * @since 2026-07-15
 **/
@Data
public class ProcessInstanceEntity {
    /**
     * 实例主键。
     */
    private String id;
    /**
     * 流程定义 ID。
     */
    private String definitionId;
    /**
     * 启动时固化的附件配置组 ID。
     */
    private String attachmentConfigId;
    /**
     * 流程编码快照。
     */
    private String processCode;
    /**
     * 流程名称快照。
     */
    private String processName;
    /**
     * 流程定义版本快照。
     */
    private Integer version;
    /**
     * 实例标题。
     */
    private String instanceTitle;
    /**
     * 外部业务键。
     */
    private String businessKey;
    /**
     * 发起人用户 ID。
     */
    private String starterUserId;
    /**
     * 发起人名称快照。
     */
    private String starterUserName;
    /**
     * 发起部门 ID。
     */
    private String starterDeptId;
    /**
     * 当前用户任务节点编码 JSON 数组。
     */
    private String currentNodeCodes;
    /**
     * 流程变量 JSON 对象，键为变量名、值为变量值。
     */
    private String variablesJson;
    /**
     * 实例状态，典型值：NOT_STARTED、RUNNING、COMPLETED、ARCHIVED、TERMINATED。
     */
    private String instanceStatus;
    /**
     * 实例开始时间。
     */
    private LocalDateTime startedAt;
    /**
     * 实例结束时间。
     */
    private LocalDateTime endedAt;
}
