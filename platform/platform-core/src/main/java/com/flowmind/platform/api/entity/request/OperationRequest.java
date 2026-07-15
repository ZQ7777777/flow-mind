package com.flowmind.platform.api.entity.request;

/**
 * 流程平台状态修改请求的基类。
 */
public class OperationRequest {

    /** 调用方生成的全局唯一操作幂等号，用于安全重试并关联首次执行结果。 */
    private String operationId;

    public OperationRequest() {
    }

    public String getOperationId() {
        return operationId;
    }

    public void setOperationId(String operationId) {
        this.operationId = operationId;
    }
}
