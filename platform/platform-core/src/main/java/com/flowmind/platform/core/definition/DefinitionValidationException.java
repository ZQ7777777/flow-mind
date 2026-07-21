package com.flowmind.platform.core.definition;

/**
 * 流程定义请求校验异常，携带 M1 冻结错误码并保持 IllegalArgumentException 兼容性。
 *
 * @author Yuxin Xu
 * @since 2026-07-20
 */
public class DefinitionValidationException extends IllegalArgumentException {

    /**
     * 错误码，典型值：FLOW_DEFINITION_INVALID、FLOW_OPERATION_ID_CONFLICT。
     */
    private final String errorCode;

    public DefinitionValidationException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public DefinitionValidationException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
