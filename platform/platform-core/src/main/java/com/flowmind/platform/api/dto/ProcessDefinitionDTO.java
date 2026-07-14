package com.flowmind.platform.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 流程定义的基础返回对象，承载流程编码、版本和发布状态等定义期元数据。
 *
 * @author Yuxin Xu
 * @created 2026-07-14
 */
@Data
public class ProcessDefinitionDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 流程定义主键。
     */
    private String id;
    /**
     * 流程编码，同一编码下允许存在多个版本。
     */
    private String processCode;
    /**
     * 流程名称。
     */
    private String processName;
    /**
     * 所属业务系统编码。
     */
    private String systemCode;
    /**
     * 流程定义版本号。
     */
    private Integer version;
    /**
     * 定义状态，如 DRAFT、PUBLISHED、ARCHIVED。
     */
    private String definitionStatus;
    /**
     * 激活状态，如 INACTIVE、ACTIVE。
     */
    private String activationStatus;
    /**
     * 灰度状态，如 OFF、ON。
     */
    private String grayStatus;
    /**
     * 灰度规则 JSON 配置。
     */
    private String grayRuleConfig;
    /**
     * 创建人。
     */
    private String createdBy;
    /**
     * 创建时间。
     */
    private LocalDateTime createdAt;
    /**
     * 最近更新人。
     */
    private String updatedBy;
    /**
     * 最近更新时间。
     */
    private LocalDateTime updatedAt;
}
