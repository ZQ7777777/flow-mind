package com.flowmind.platform.api.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * REST 接口错误响应。
 *
 * @author FlowMind
 * @since 2026-07-23
 */
@Data
public class ApiErrorDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * HTTP 状态码，例如 400、409、503。
     */
    private Integer status;

    /**
     * 稳定错误码，例如 FLOW_DEFINITION_INVALID、FLOW_DATABASE_BUSY。
     */
    private String code;

    /**
     * 错误说明，用于前端调试页或调用方展示。
     */
    private String message;
}
