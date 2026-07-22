package com.flowmind.platform.core.runtime;

/**
 * 运行时请求或权限校验未通过时抛出的异常。
 *
 * @author FlowMind
 * @since 2026-07-22
 */
public class RuntimeValidationException extends IllegalArgumentException {

    /** 冻结的对外错误码。 */
    private final String errorCode;

    public RuntimeValidationException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
