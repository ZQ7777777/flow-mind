package com.flowmind.platform.core.monitor;

import com.flowmind.platform.api.enums.ReminderTypeEnum;
import com.flowmind.platform.persistence.repository.ReminderRecordRepository;
import org.springframework.stereotype.Component;

/** Deduplicates automatic reminder creation by task and reminder type. */
@Component
public class ReminderDeduplicationGuard {

    private final ReminderRecordRepository reminderRepository;

    public ReminderDeduplicationGuard(ReminderRecordRepository reminderRepository) {
        this.reminderRepository = reminderRepository;
    }

    public boolean canCreate(String taskId, ReminderTypeEnum reminderType, int maxCount) {
        return reminderRepository.countByTaskAndType(taskId, reminderType.name()) < maxCount;
    }
}
