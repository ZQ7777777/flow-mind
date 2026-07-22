package com.flowmind.platform.core.runtime;

import com.flowmind.platform.api.dto.UserContext;
import com.flowmind.platform.api.enums.ActionTypeEnum;
import com.flowmind.platform.persistence.entity.ProcessInstanceEntity;

/**
 * 运行期实例动作校验通过后的只读上下文。
 */
public class RuntimeInstanceContext {

    private final ProcessInstanceEntity instance;
    private final ActionTypeEnum actionType;
    private final UserContext operator;

    public RuntimeInstanceContext(ProcessInstanceEntity instance,
                                  ActionTypeEnum actionType,
                                  UserContext operator) {
        this.instance = instance;
        this.actionType = actionType;
        this.operator = operator;
    }

    public ProcessInstanceEntity getInstance() {
        return instance;
    }

    public ActionTypeEnum getActionType() {
        return actionType;
    }

    public UserContext getOperator() {
        return operator;
    }
}
