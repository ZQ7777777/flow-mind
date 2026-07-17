package com.flowmind.platform.api.request;

import java.time.LocalDateTime;

/**
 * 超时任务扫描请求。
 */
public class TimeoutScanRequest extends OperationRequest {

    /** 扫描基准时间。 */
    private LocalDateTime scanAt;
    /** 最大扫描数量。 */
    private Integer limit;

    public TimeoutScanRequest() {
    }

    public LocalDateTime getScanAt() {
        return scanAt;
    }

    public void setScanAt(LocalDateTime scanAt) {
        this.scanAt = scanAt;
    }

    public Integer getLimit() {
        return limit;
    }

    public void setLimit(Integer limit) {
        this.limit = limit;
    }
}
