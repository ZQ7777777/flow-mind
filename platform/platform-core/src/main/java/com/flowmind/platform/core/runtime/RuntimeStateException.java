package com.flowmind.platform.core.runtime;

/**
 * 运行时实体、定义或幂等记录状态不允许继续处理时抛出的异常。
 *
 * @author FlowMind
 * @since 2026-07-22
 */
public class RuntimeStateException extends IllegalStateException {

    /** 冻结的对外错误码。 */
    private final String errorCode;

    public RuntimeStateException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
