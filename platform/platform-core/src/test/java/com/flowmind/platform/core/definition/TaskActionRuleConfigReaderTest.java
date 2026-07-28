package com.flowmind.platform.core.definition;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskActionRuleConfigReaderTest {

    private final TaskActionRuleConfigReader reader = new TaskActionRuleConfigReader();

    @Test
    void readsTaskActionRulesFromListenerConfig() {
        TaskActionRuleConfigReader.TaskActionRules rules = reader.read(
                "{\"taskActionRules\":{\"reject\":{\"enabled\":true,"
                        + "\"targetNodeCodes\":[\"draft\",\"manager\"]},"
                        + "\"directSend\":{\"enabled\":true,\"targetMode\":\"REJECT_SOURCE\"}}}");

        assertTrue(rules.isRejectEnabled());
        assertEquals(Arrays.asList("draft", "manager"), rules.getRejectTargetNodeCodes());
        assertTrue(rules.isDirectSendEnabled());
        assertEquals("REJECT_SOURCE", rules.getDirectSendTargetMode());
    }

    @Test
    void treatsBlankListenerConfigAsEmptyRules() {
        TaskActionRuleConfigReader.TaskActionRules rules = reader.read(null);

        assertFalse(rules.isRejectEnabled());
        assertTrue(rules.getRejectTargetNodeCodes().isEmpty());
        assertFalse(rules.isDirectSendEnabled());
    }

    @Test
    void rejectsInvalidTaskActionRuleShape() {
        assertThrows(IllegalArgumentException.class, new org.junit.jupiter.api.function.Executable() {
            @Override
            public void execute() {
                reader.read("{\"taskActionRules\":{\"reject\":{\"enabled\":\"true\"}}}");
            }
        });
    }
}
