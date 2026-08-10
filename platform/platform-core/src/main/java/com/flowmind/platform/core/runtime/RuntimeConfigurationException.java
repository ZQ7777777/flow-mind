package com.flowmind.platform.core.runtime;

import com.flowmind.platform.api.error.PlatformError;
import com.flowmind.platform.api.error.PlatformErrorCategories;
import com.flowmind.platform.api.error.PlatformErrorCategory;

/**
 * 运行时定义配置解析异常。
 *
 * @author Yuxin Xu
 * @since 2026-07-22
 */
public class RuntimeConfigurationException extends IllegalArgumentException implements PlatformError {

    /**
     * 错误码，典型值：FLOW_RUNTIME_NODE_CONFIG_INVALID、FLOW_RUNTIME_CONDITION_EXPRESSION_INVALID。
     */
    private final String errorCode;

    /**
     * 使用错误码和错误信息创建异常。
     *
     * @param errorCode 错误码
     * @param message 错误信息
     */
    public RuntimeConfigurationException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    /**
     * 使用错误码、错误信息和根因创建异常。
     *
     * @param errorCode 错误码
     * @param message 错误信息
     * @param cause 根因异常
     */
    public RuntimeConfigurationException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    /**
     * 获取错误码。
     *
     * @return 错误码
     */
    public String getErrorCode() {
        return errorCode;
    }

    @Override
    public PlatformErrorCategory getErrorCategory() {
        return PlatformErrorCategories.resolve(errorCode, PlatformErrorCategory.INVALID_REQUEST);
    }
}
