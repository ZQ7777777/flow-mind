package com.flowmind.platform.api.request;

import java.util.Map;

public class DirectSendRequest extends TaskOperationRequest {

    /**
     * @deprecated 仅为兼容旧调用方保留；直送目标由平台根据可信驳回历史解析，执行时忽略该字段。
     */
    @Deprecated
    private String targetNodeCode;
    /** 申请节点返工直送时要合并写入的流程变量；普通用户任务必须为空。 */
    private Map<String, Object> variables;

    public DirectSendRequest() {
    }

    public String getTargetNodeCode() {
        return targetNodeCode;
    }

    public void setTargetNodeCode(String targetNodeCode) {
        this.targetNodeCode = targetNodeCode;
    }

    public Map<String, Object> getVariables() {
        return variables;
    }

    public void setVariables(Map<String, Object> variables) {
        this.variables = variables;
    }
}
