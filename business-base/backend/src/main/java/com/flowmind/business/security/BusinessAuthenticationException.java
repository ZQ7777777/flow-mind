package com.flowmind.business.security;

/**
 * 可信业务身份缺失或无效。
 *
 * @author FlowMind
 * @since 2026-08-10
 */
public class BusinessAuthenticationException extends RuntimeException {

    public BusinessAuthenticationException(String message) {
        super(message);
    }
}
