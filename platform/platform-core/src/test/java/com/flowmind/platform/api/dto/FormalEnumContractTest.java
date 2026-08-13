package com.flowmind.platform.api.dto;

import com.flowmind.platform.api.enums.ActivationStatusEnum;
import com.flowmind.platform.api.enums.AlertSeverityEnum;
import com.flowmind.platform.api.enums.AlertTypeEnum;
import com.flowmind.platform.api.enums.ApproverRuleTypeEnum;
import com.flowmind.platform.api.enums.AttachmentConfigStatusEnum;
import com.flowmind.platform.api.enums.AttachmentOwnerTypeEnum;
import com.flowmind.platform.api.enums.AttachmentTemplateStatusEnum;
import com.flowmind.platform.api.enums.DefinitionStatusEnum;
import com.flowmind.platform.api.enums.GrayStatusEnum;
import com.flowmind.platform.api.enums.HandleTypeEnum;
import com.flowmind.platform.api.enums.InstanceStatusEnum;
import com.flowmind.platform.api.enums.MultiInstanceModeEnum;
import com.flowmind.platform.api.enums.NodeTypeEnum;
import com.flowmind.platform.api.enums.OperationTargetTypeEnum;
import com.flowmind.platform.api.enums.ReminderTypeEnum;
import com.flowmind.platform.api.enums.TaskStatusEnum;
import com.flowmind.platform.api.enums.WorkflowEventTypeEnum;
import com.flowmind.platform.api.request.AttachmentUploadItem;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FormalEnumContractTest {

    @Test
    void publicContractsUseFormalEnumsForFrozenFiniteValues() throws NoSuchFieldException {
        assertFieldType(ProcessInstanceDTO.class, "instanceStatus", InstanceStatusEnum.class);
        assertFieldType(TaskDTO.class, "taskStatus", TaskStatusEnum.class);
        assertFieldType(HistoryTaskDTO.class, "handleType", HandleTypeEnum.class);
        assertFieldType(HistoryTaskDTO.class, "actionType", com.flowmind.platform.api.enums.ActionTypeEnum.class);
        assertFieldType(OperationResult.class, "targetType", OperationTargetTypeEnum.class);
        assertFieldType(AuditLogDTO.class, "targetType", OperationTargetTypeEnum.class);
        assertFieldType(AuditLogQuery.class, "targetType", OperationTargetTypeEnum.class);
        assertFieldType(AuditLogDTO.class, "targetId", String.class);
        assertFieldType(AuditLogQuery.class, "targetId", String.class);
        assertFieldType(AttachmentUploadItem.class, "ownerType", AttachmentOwnerTypeEnum.class);
        assertFieldType(AdminInstanceQuery.class, "instanceStatus", InstanceStatusEnum.class);
        assertFieldType(CallbackLogQuery.class, "eventType", WorkflowEventTypeEnum.class);
        assertFieldType(ProcessDefinitionDTO.class, "definitionStatus", DefinitionStatusEnum.class);
        assertFieldType(ProcessDefinitionDTO.class, "activationStatus", ActivationStatusEnum.class);
        assertFieldType(ProcessDefinitionDTO.class, "grayStatus", GrayStatusEnum.class);
        assertFieldType(ProcessNodeDTO.class, "nodeType", NodeTypeEnum.class);
        assertFieldType(ProcessNodeDTO.class, "approverRuleType", ApproverRuleTypeEnum.class);
        assertFieldType(ProcessNodeDTO.class, "multiInstanceMode", MultiInstanceModeEnum.class);
        assertFieldType(ProcessAttachmentTemplateDTO.class, "configStatus", AttachmentConfigStatusEnum.class);
        assertFieldType(ProcessAttachmentTemplateDTO.class, "templateStatus", AttachmentTemplateStatusEnum.class);
        assertFieldType(AlertDTO.class, "alertType", AlertTypeEnum.class);
        assertFieldType(AlertDTO.class, "severity", AlertSeverityEnum.class);
        assertFieldType(ReminderDTO.class, "reminderType", ReminderTypeEnum.class);
    }

    @Test
    void newlyAddedEnumsMatchTheValuesFrozenByThePublicDesign() {
        assertEquals(Arrays.asList("NORMAL", "DELEGATE", "TRANSFER", "ADMIN_PROXY"),
                enumNames(HandleTypeEnum.values()));
        assertEquals(Arrays.asList("DEFINITION", "INSTANCE", "TASK", "ATTACHMENT"),
                enumNames(OperationTargetTypeEnum.values()));
        assertEquals(Arrays.asList("DRAFT", "ACTIVE", "INACTIVE"),
                enumNames(AttachmentConfigStatusEnum.values()));
        assertEquals(Arrays.asList("ENABLED", "DISABLED"),
                enumNames(AttachmentTemplateStatusEnum.values()));
        assertEquals(Arrays.asList("MANUAL", "AUTO", "DUE_SOON", "TIMEOUT"),
                enumNames(ReminderTypeEnum.values()));
        assertEquals(Arrays.asList("TASK_TIMEOUT", "CALLBACK_FAILED", "ACTION_EXCEPTION"),
                enumNames(AlertTypeEnum.values()));
        assertEquals(Arrays.asList("LOW", "MEDIUM", "HIGH"),
                enumNames(AlertSeverityEnum.values()));
    }

    private void assertFieldType(Class<?> owner, String fieldName, Class<?> expectedType)
            throws NoSuchFieldException {
        assertEquals(expectedType, owner.getDeclaredField(fieldName).getType());
    }

    private java.util.List<String> enumNames(Enum<?>[] values) {
        String[] names = new String[values.length];
        for (int index = 0; index < values.length; index++) {
            names[index] = values[index].name();
        }
        return Arrays.asList(names);
    }
}
