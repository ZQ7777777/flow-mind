package com.flowmind.business.common;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 基于可信上下文生成稳定且动作隔离的平台幂等操作号。
 *
 * @author FlowMind
 * @since 2026-08-10
 */
@Component
public class OperationIdFactory {

    public String create(String businessDomain,
                         String action,
                         String resourceId,
                         String trustedUserId,
                         String idempotencyKey) {
        String canonical = require(businessDomain, "businessDomain") + "|"
                + require(action, "action") + "|"
                + require(resourceId, "resourceId") + "|"
                + require(trustedUserId, "trustedUserId") + "|"
                + require(idempotencyKey, "idempotencyKey");
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                result.append(String.format("%02x", item & 0xff));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String require(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value.trim();
    }
}
