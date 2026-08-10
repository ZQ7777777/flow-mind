package com.flowmind.platform.api.service;

import com.flowmind.platform.api.dto.TaskDTO;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TaskQueryServiceContractTest {

    @Test
    void exposesSingleTaskLookup() throws Exception {
        Method method = TaskQueryService.class.getMethod("getTask", String.class);

        assertEquals(TaskDTO.class, method.getReturnType());
    }
}
