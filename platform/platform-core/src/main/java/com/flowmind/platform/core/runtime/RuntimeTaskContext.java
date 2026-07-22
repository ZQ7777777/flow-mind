package com.flowmind.platform.core.runtime;

import com.flowmind.platform.api.dto.UserContext;
import com.flowmind.platform.api.enums.ActionTypeEnum;
import com.flowmind.platform.persistence.entity.ProcessActiveTaskEntity;
import com.flowmind.platform.persistence.entity.ProcessInstanceEntity;

/**
 * 运行期任务动作校验通过后的只读上下文。
 */
public class RuntimeTaskContext extends RuntimeInstanceContext {

    private final ProcessActiveTaskEntity task;

    public RuntimeTaskContext(ProcessInstanceEntity instance,
                              ProcessActiveTaskEntity task,
                              ActionTypeEnum actionType,
                              UserContext operator) {
        super(instance, actionType, operator);
        this.task = task;
    }

    public ProcessActiveTaskEntity getTask() {
        return task;
    }
}
