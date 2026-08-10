package com.flowmind.business.security;

/**
 * 业务资源访问被拒绝。
 *
 * @author FlowMind
 * @since 2026-08-10
 */
public class BusinessAccessDeniedException extends RuntimeException {

    public BusinessAccessDeniedException(String message) {
        super(message);
    }
}
