package com.flowmind.platform.api.service;

import com.flowmind.platform.api.dto.ReadRecordDTO;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReadRecordServiceContractTest {

    @Test
    void markReadUsesOnlyPublicApiTypes() throws NoSuchMethodException {
        Method method = ReadRecordService.class.getMethod("markRead", String.class);

        assertEquals(ReadRecordDTO.class, method.getReturnType());
        assertEquals(String.class, method.getParameterTypes()[0]);
    }
}
