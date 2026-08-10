package com.flowmind.business.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OperationIdFactoryTest {

    private final OperationIdFactory factory = new OperationIdFactory();

    @Test
    void sameTrustedTupleProducesStableSha256() {
        String first = factory.create("workflow", "approve", "task-1", "user-1", "key-1");
        String replay = factory.create("workflow", "approve", "task-1", "user-1", "key-1");

        assertEquals(first, replay);
        assertEquals(64, first.length());
    }

    @Test
    void differentActionsCannotShareOperationId() {
        assertNotEquals(
                factory.create("workflow", "approve", "task-1", "user-1", "key-1"),
                factory.create("workflow", "reject", "task-1", "user-1", "key-1"));
    }

    @Test
    void everyTuplePartIsRequired() {
        assertThrows(IllegalArgumentException.class,
                () -> factory.create("workflow", " ", "task-1", "user-1", "key-1"));
    }
}
