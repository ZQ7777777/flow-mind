package com.flowmind.platform.core.validation;

/**
 * 冻结规则校验失败时抛出的业务异常。
 *
 * <p>异常中保留稳定错误码，供接口层、幂等记录或前端展示按业务错误处理。</p>
 *
 * @author Yuxin Xu
 * @since 2026-07-15
 */
public class FrozenValidationException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final String errorCode;

    public FrozenValidationException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
