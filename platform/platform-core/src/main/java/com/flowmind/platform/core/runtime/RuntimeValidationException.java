package com.flowmind.platform.core.runtime;

/**
 * 运行期确定性校验异常，携带稳定错误码供幂等失败记录和调用方映射。
 */
public class RuntimeValidationException extends IllegalStateException {

    private final String errorCode;

    public RuntimeValidationException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public RuntimeValidationException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
