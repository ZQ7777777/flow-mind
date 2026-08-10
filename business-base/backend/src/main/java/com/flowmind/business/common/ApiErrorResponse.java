package com.flowmind.business.common;

import java.util.Collections;
import java.util.Map;

/**
 * 业务后端统一错误响应。
 *
 * @author FlowMind
 * @since 2026-08-10
 */
public class ApiErrorResponse {

    private int status;
    private String code;
    private String message;
    private String requestId;
    private Map<String, Object> details = Collections.emptyMap();

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public Map<String, Object> getDetails() {
        return details;
    }

    public void setDetails(Map<String, Object> details) {
        this.details = details == null ? Collections.<String, Object>emptyMap() : details;
    }
}
