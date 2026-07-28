package com.flowmind.platform.core.monitor;

import com.flowmind.platform.api.dto.AlertDTO;
import com.flowmind.platform.api.dto.ReminderDTO;
import com.flowmind.platform.api.enums.AlertSeverityEnum;
import com.flowmind.platform.api.enums.AlertStatusEnum;
import com.flowmind.platform.api.enums.AlertTypeEnum;
import com.flowmind.platform.api.enums.ReminderStatusEnum;
import com.flowmind.platform.api.enums.ReminderTypeEnum;
import com.flowmind.platform.core.runtime.RuntimeJsonCodec;
import com.flowmind.platform.persistence.entity.ProcessAlertRecordEntity;
import com.flowmind.platform.persistence.entity.ProcessReminderRecordEntity;
import org.springframework.stereotype.Component;

/** Maps monitor persistence entities to public DTOs. */
@Component
public class MonitorModelMapper {

    public ReminderDTO toDTO(ProcessReminderRecordEntity entity) {
        ReminderDTO dto = new ReminderDTO();
        dto.setReminderId(entity.getId());
        dto.setInstanceId(entity.getInstanceId());
        dto.setTaskId(entity.getTaskId());
        dto.setReminderType(toEnum(ReminderTypeEnum.class, entity.getReminderType()));
        dto.setTargetUserIds(RuntimeJsonCodec.readStringList(entity.getTargetUserIds()));
        dto.setMessage(entity.getMessage());
        dto.setReminderStatus(toEnum(ReminderStatusEnum.class, entity.getReminderStatus()));
        dto.setErrorMessage(entity.getErrorMessage());
        dto.setCreatedBy(entity.getCreatedBy());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setSentAt(entity.getSentAt());
        return dto;
    }

    public AlertDTO toDTO(ProcessAlertRecordEntity entity) {
        AlertDTO dto = new AlertDTO();
        dto.setAlertId(entity.getId());
        dto.setInstanceId(entity.getInstanceId());
        dto.setTaskId(entity.getTaskId());
        dto.setAlertType(toEnum(AlertTypeEnum.class, entity.getAlertType()));
        dto.setSeverity(toEnum(AlertSeverityEnum.class, entity.getSeverity()));
        dto.setAlertStatus(toEnum(AlertStatusEnum.class, entity.getAlertStatus()));
        dto.setDetail(RuntimeJsonCodec.readObjectMap(entity.getDetailJson()));
        dto.setHandledBy(entity.getHandledBy());
        dto.setHandledAt(entity.getHandledAt());
        dto.setCreatedAt(entity.getCreatedAt());
        return dto;
    }

    private <T extends Enum<T>> T toEnum(Class<T> type, String value) {
        return value == null || value.trim().isEmpty() ? null : Enum.valueOf(type, value);
    }
}
