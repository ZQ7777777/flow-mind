package com.flowmind.platform.api.request;

import java.util.Map;

public class DirectSendRequest extends TaskOperationRequest {

    /** 直送操作要回到的目标用户任务节点编码。 */
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
