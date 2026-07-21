package com.flowmind.platform.core.definition;

/**
 * 流程定义状态异常，携带 M1 冻结错误码并保持 IllegalStateException 兼容性。
 *
 * @author Yuxin Xu
 * @since 2026-07-20
 */
public class DefinitionStateException extends IllegalStateException {

    /**
     * 错误码，典型值：FLOW_DEFINITION_NOT_EDITABLE、FLOW_OPERATION_IN_PROGRESS。
     */
    private final String errorCode;

    public DefinitionStateException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public DefinitionStateException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
