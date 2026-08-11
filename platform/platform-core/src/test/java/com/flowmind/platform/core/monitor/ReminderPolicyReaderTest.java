package com.flowmind.platform.core.monitor;

import com.flowmind.platform.core.runtime.RuntimeErrorCodes;
import com.flowmind.platform.core.runtime.RuntimeValidationException;
import com.flowmind.platform.persistence.entity.ProcessNodeEntity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReminderPolicyReaderTest {

    private final ReminderPolicyReader reader = new ReminderPolicyReader();

    @Test
    void defaultsBeforeDueMinutesToThirty() {
        ProcessNodeEntity node = node("{\"enabled\":true}");

        assertEquals(Integer.valueOf(30), reader.read(node).getBeforeDueMinutes());
    }

    @Test
    void readsPositiveBeforeDueMinutes() {
        ProcessNodeEntity node = node("{\"enabled\":true,\"beforeDueMinutes\":45}");

        assertEquals(Integer.valueOf(45), reader.read(node).getBeforeDueMinutes());
    }

    @Test
    void rejectsNonPositiveBeforeDueMinutes() {
        ProcessNodeEntity node = node("{\"enabled\":true,\"beforeDueMinutes\":0}");

        RuntimeValidationException error = assertThrows(RuntimeValidationException.class,
                () -> reader.read(node));

        assertEquals(RuntimeErrorCodes.NODE_CONFIG_INVALID, error.getErrorCode());
    }

    @Test
    void rejectsFractionalBeforeDueMinutes() {
        ProcessNodeEntity node = node("{\"enabled\":true,\"beforeDueMinutes\":1.5}");

        assertThrows(RuntimeValidationException.class, () -> reader.read(node));
    }

    private ProcessNodeEntity node(String reminderConfig) {
        ProcessNodeEntity node = new ProcessNodeEntity();
        node.setReminderConfig(reminderConfig);
        return node;
    }
}
