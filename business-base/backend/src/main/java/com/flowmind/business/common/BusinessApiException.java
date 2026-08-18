package com.flowmind.business.common;

import org.springframework.http.HttpStatus;

/**
 * 业务基座主动返回的稳定 HTTP 异常。
 *
 * @author FlowMind
 * @since 2026-08-18
 */
public class BusinessApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public BusinessApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
