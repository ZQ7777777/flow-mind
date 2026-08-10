package com.flowmind.platform.core.runtime;

import com.flowmind.platform.api.error.PlatformError;
import com.flowmind.platform.api.error.PlatformErrorCategories;
import com.flowmind.platform.api.error.PlatformErrorCategory;

/**
 * 运行时请求或权限校验未通过时抛出的异常。
 *
 * @author FlowMind
 * @since 2026-07-22
 * 运行期确定性校验异常，携带稳定错误码供幂等失败记录和调用方映射。
 */
public class RuntimeValidationException extends IllegalArgumentException implements PlatformError {


    /** 冻结的对外错误码。 */
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

    @Override
    public PlatformErrorCategory getErrorCategory() {
        return PlatformErrorCategories.resolve(errorCode, PlatformErrorCategory.INVALID_REQUEST);
    }
}
