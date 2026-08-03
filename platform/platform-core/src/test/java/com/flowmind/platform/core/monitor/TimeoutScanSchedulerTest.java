package com.flowmind.platform.core.monitor;

import com.flowmind.platform.api.dto.UserContext;
import com.flowmind.platform.api.request.TimeoutScanRequest;
import com.flowmind.platform.api.service.ProcessMonitorService;
import com.flowmind.platform.core.runtime.SystemOperatorContext;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TimeoutScanSchedulerTest {

    @Test
    void scanOnceRunsNonDryRunAsSystemOperator() {
        ProcessMonitorService monitorService = mock(ProcessMonitorService.class);
        when(monitorService.scanTimeoutTasks(org.mockito.ArgumentMatchers.any(TimeoutScanRequest.class)))
                .thenAnswer(invocation -> {
                    UserContext operator = SystemOperatorContext.current();
                    assertEquals("system_timeout", operator.getUserId());
                    return Collections.emptyList();
                });
        TimeoutScanScheduler scheduler = new TimeoutScanScheduler(monitorService,
                false, 0L, 1000L, 25, "system_timeout");

        scheduler.scanOnce();

        ArgumentCaptor<TimeoutScanRequest> captor = ArgumentCaptor.forClass(TimeoutScanRequest.class);
        verify(monitorService).scanTimeoutTasks(captor.capture());
        assertFalse(captor.getValue().getDryRun());
        assertEquals(Integer.valueOf(25), captor.getValue().getLimit());
        assertEquals("system_timeout", captor.getValue().getOperatorUserId());
        assertNull(SystemOperatorContext.current());
    }
}
