package com.flowmind.platform.persistence.entity;

import lombok.Data;
import java.time.LocalDateTime;

/** 流程定义表 process_definition 的持久化实体。
 *
 * @author Yuxin Xu
 * @since 2026-07-15
 **/
@Data
public class ProcessDefinitionEntity {
    /**
     * 流程定义主键。
     */
    private String id;
    /**
     * 流程编码，同一编码可有多个版本。
     */
    private String processCode;
    /**
     * 流程名称。
     */
    private String processName;
    /**
     * 所属系统编码。
     */
    private String systemCode;
    /**
     * 流程定义版本号。
     */
    private Integer version;
    /**
     * 定义状态，典型值：DRAFT、PUBLISHED、ARCHIVED。
     */
    private String definitionStatus;
    /**
     * 激活状态，典型值：INACTIVE、ACTIVE。
     */
    private String activationStatus;
    /**
     * 灰度状态，典型值：OFF、ON。
     */
    private String grayStatus;
    /**
     * 灰度规则 JSON，包含用户、部门、角色或比例命中规则。
     */
    private String grayRuleConfig;
    /**
     * 归档操作人 ID。
     */
    private String archivedBy;
    /**
     * 归档时间。
     */
    private LocalDateTime archivedAt;
    /**
     * 定义备注。
     */
    private String remark;
    /**
     * 创建人 ID。
     */
    private String createdBy;
    /**
     * 创建时间。
     */
    private LocalDateTime createdAt;
    /**
     * 最近更新人 ID。
     */
    private String updatedBy;
    /**
     * 最近更新时间。
     */
    private LocalDateTime updatedAt;
}
