package com.flowmind.platform.api.request;

import com.flowmind.platform.api.enums.AlertStatusEnum;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeRequestContractTest {

    @ParameterizedTest(name = "{0}")
    @MethodSource("validRequestFixtures")
    void everyRuntimeRequestProvidesItsContractFields(RequestFixture fixture) throws Exception {
        OperationRequest request = fixture.getRequest();

        assertTrue(OperationRequest.class.isAssignableFrom(fixture.getRequestType()));
        assertNotNull(request.getOperationId());
        assertFalse(request.getOperationId().isEmpty());
        if (request instanceof TaskOperationRequest) {
            TaskOperationRequest taskRequest = (TaskOperationRequest) request;
            assertTrue(TaskOperationRequest.class.isAssignableFrom(fixture.getRequestType()));
            assertNotNull(taskRequest.getTaskId());
            assertNotNull(taskRequest.getExpectedTaskVersion());
            assertNotNull(taskRequest.getOperatorUserId());
        }
        assertPropertyValues(request, fixture.getProperties());
    }

    @Test
    void attachmentUploadItemProvidesAllUploadProperties() throws Exception {
        AttachmentUploadItem attachment = attachmentFixture();

        assertPropertyValues(attachment, attachmentProperties());
    }

    @Test
    void jumpNodeRequestIsInstanceLevelOnly() {
        assertFalse(TaskOperationRequest.class.isAssignableFrom(JumpNodeRequest.class));
    }

    @Test
    void requestFixturesCoverEveryRuntimeModificationRequestExactlyOnce() throws Exception {
        List<RequestFixture> fixtures = validRequestFixtures().collect(Collectors.toList());
        Set<Class<? extends OperationRequest>> coveredTypes =
                new LinkedHashSet<Class<? extends OperationRequest>>();

        for (RequestFixture fixture : fixtures) {
            assertTrue(coveredTypes.add(fixture.getRequestType()),
                    fixture.getRequestType().getSimpleName() + " has duplicate fixtures");
        }

        assertEquals(runtimeModificationRequestTypes(), coveredTypes);
        assertEquals(runtimeModificationRequestTypes().size(), fixtures.size());
    }

    private static Stream<RequestFixture> validRequestFixtures() throws Exception {
        AttachmentUploadItem attachment = attachmentFixture();
        Map<String, Object> startVariables = variables("amount", 10);
        Map<String, Object> updateVariables = variables("approved", Boolean.TRUE);
        Map<String, Object> submitVariables = variables("submitted", Boolean.TRUE);
        return Stream.of(
                fixture("start process", StartProcessRequest.class, properties(
                        "operationId", "start-operation",
                        "processCode", "process-code-001",
                        "businessKey", "business-001",
                        "instanceTitle", "approval request",
                        "starterUserId", "starter-001",
                        "starterDeptId", "dept-001",
                        "variables", startVariables)),
                fixture("update variables", UpdateVariablesRequest.class, properties(
                        "operationId", "update-operation",
                        "instanceId", "instance-001",
                        "operatorUserId", "operator-001",
                        "variables", updateVariables)),
                taskFixture("submit", SubmitTaskRequest.class, properties(
                        "variables", submitVariables,
                        "attachments", Collections.singletonList(attachment))),
                taskFixture("approve", ApproveTaskRequest.class, Collections.<String, Object>emptyMap()),
                taskFixture("reject", RejectTaskRequest.class, properties("targetNodeCode", "rework")),
                taskFixture("return", ReturnTaskRequest.class, Collections.<String, Object>emptyMap()),
                taskFixture("withdraw", WithdrawTaskRequest.class, Collections.<String, Object>emptyMap()),
                taskFixture("direct-send", DirectSendRequest.class, properties("targetNodeCode", "archive")),
                taskFixture("transfer", TransferTaskRequest.class, properties("targetUserId", "user-002")),
                taskFixture("add-sign", AddSignRequest.class, properties(
                        "addSignUserIds", Arrays.asList("user-003", "user-004"))),
                taskFixture("claim", ClaimTaskRequest.class, Collections.<String, Object>emptyMap()),
                taskFixture("unclaim", UnclaimTaskRequest.class, Collections.<String, Object>emptyMap()),
                fixture("terminate process", TerminateProcessRequest.class, properties(
                        "operationId", "terminate-operation",
                        "instanceId", "instance-002",
                        "operatorUserId", "operator-002",
                        "comment", "terminate request")),
                fixture("delete process instance", DeleteProcessInstanceRequest.class, properties(
                        "operationId", "delete-operation",
                        "instanceId", "instance-003",
                        "operatorUserId", "operator-003")),
                fixture("jump node", JumpNodeRequest.class, properties(
                        "operationId", "jump-operation",
                        "instanceId", "instance-004",
                        "targetNodeCode", "complete",
                        "operatorUserId", "operator-004",
                        "comment", "jump request")),
                fixture("force complete", ForceCompleteRequest.class, properties(
                        "operationId", "force-complete-operation",
                        "instanceId", "instance-005",
                        "operatorUserId", "operator-005",
                        "comment", "force complete request")),
                taskFixture("remind", RemindTaskRequest.class, Collections.<String, Object>emptyMap()),
                fixture("save instance attachment", SaveInstanceAttachmentRequest.class, properties(
                        "operationId", "save-instance-attachment-operation",
                        "instanceId", "instance-006",
                        "operatorUserId", "operator-006",
                        "attachment", attachment)),
                taskFixture("save task attachment", SaveTaskAttachmentRequest.class, properties(
                        "instanceId", "instance-007",
                        "attachment", attachment)),
                fixture("delete attachment", DeleteAttachmentRequest.class, properties(
                        "operationId", "delete-attachment-operation",
                        "attachmentId", "attachment-001",
                        "operatorUserId", "operator-007")),
                fixture("handle alert", HandleAlertRequest.class, properties(
                        "operationId", "handle-alert-operation",
                        "alertId", "alert-001",
                        "operatorUserId", "operator-008",
                        "targetStatus", AlertStatusEnum.HANDLED,
                        "comment", "handled")));
    }

    private static Set<Class<? extends OperationRequest>> runtimeModificationRequestTypes() {
        return new LinkedHashSet<Class<? extends OperationRequest>>(
                Arrays.<Class<? extends OperationRequest>>asList(
                        StartProcessRequest.class,
                        UpdateVariablesRequest.class,
                        SubmitTaskRequest.class,
                        ApproveTaskRequest.class,
                        RejectTaskRequest.class,
                        ReturnTaskRequest.class,
                        WithdrawTaskRequest.class,
                        DirectSendRequest.class,
                        TransferTaskRequest.class,
                        AddSignRequest.class,
                        ClaimTaskRequest.class,
                        UnclaimTaskRequest.class,
                        TerminateProcessRequest.class,
                        DeleteProcessInstanceRequest.class,
                        JumpNodeRequest.class,
                        ForceCompleteRequest.class,
                        RemindTaskRequest.class,
                        SaveInstanceAttachmentRequest.class,
                        SaveTaskAttachmentRequest.class,
                        DeleteAttachmentRequest.class,
                        HandleAlertRequest.class));
    }

    private static RequestFixture taskFixture(String name,
                                              Class<? extends TaskOperationRequest> requestType,
                                              Map<String, Object> specificProperties) throws Exception {
        Map<String, Object> properties = properties(
                "operationId", name + "-operation",
                "taskId", name + "-task",
                "expectedTaskVersion", Long.valueOf(2L),
                "operatorUserId", name + "-operator",
                "comment", name + " comment");
        properties.putAll(specificProperties);
        return fixture(name, requestType, properties);
    }

    private static RequestFixture fixture(String name,
                                          Class<? extends OperationRequest> requestType,
                                          Map<String, Object> properties) throws Exception {
        OperationRequest request = requestType.getDeclaredConstructor().newInstance();
        applyProperties(request, properties);
        return new RequestFixture(name, requestType, request, properties);
    }

    private static AttachmentUploadItem attachmentFixture() throws Exception {
        AttachmentUploadItem attachment = new AttachmentUploadItem();
        applyProperties(attachment, attachmentProperties());
        return attachment;
    }

    private static Map<String, Object> attachmentProperties() {
        return properties(
                "attachmentCode", "receipt",
                "ownerType", "INSTANCE",
                "fileName", "receipt.pdf",
                "contentType", "application/pdf",
                "sizeBytes", Long.valueOf(3L),
                "content", new byte[] {1, 2, 3});
    }

    private static Map<String, Object> variables(String name, Object value) {
        return properties(name, value);
    }

    private static Map<String, Object> properties(Object... values) {
        Map<String, Object> properties = new LinkedHashMap<String, Object>();
        for (int index = 0; index < values.length; index += 2) {
            properties.put((String) values[index], values[index + 1]);
        }
        return properties;
    }

    private static void applyProperties(Object bean, Map<String, Object> properties) throws Exception {
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            PropertyDescriptor descriptor = propertyDescriptor(bean.getClass(), entry.getKey());
            Method writeMethod = descriptor.getWriteMethod();
            assertNotNull(writeMethod, entry.getKey() + " must have a setter");
            writeMethod.invoke(bean, entry.getValue());
        }
    }

    private static void assertPropertyValues(Object bean, Map<String, Object> properties) throws Exception {
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            PropertyDescriptor descriptor = propertyDescriptor(bean.getClass(), entry.getKey());
            Method readMethod = descriptor.getReadMethod();
            assertNotNull(readMethod, entry.getKey() + " must have a getter");
            Object actualValue = readMethod.invoke(bean);
            if (entry.getValue() instanceof byte[]) {
                assertTrue(actualValue instanceof byte[]);
                assertArrayEquals((byte[]) entry.getValue(), (byte[]) actualValue);
            } else {
                assertEquals(entry.getValue(), actualValue);
            }
        }
    }

    private static PropertyDescriptor propertyDescriptor(Class<?> type, String propertyName) throws Exception {
        for (PropertyDescriptor descriptor : Introspector.getBeanInfo(type).getPropertyDescriptors()) {
            if (propertyName.equals(descriptor.getName())) {
                return descriptor;
            }
        }
        throw new AssertionError(type.getName() + " is missing property " + propertyName);
    }

    private static final class RequestFixture {

        private final String name;
        private final Class<? extends OperationRequest> requestType;
        private final OperationRequest request;
        private final Map<String, Object> properties;

        private RequestFixture(String name,
                               Class<? extends OperationRequest> requestType,
                               OperationRequest request,
                               Map<String, Object> properties) {
            this.name = name;
            this.requestType = requestType;
            this.request = request;
            this.properties = properties;
        }

        private Class<? extends OperationRequest> getRequestType() {
            return requestType;
        }

        private OperationRequest getRequest() {
            return request;
        }

        private Map<String, Object> getProperties() {
            return properties;
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
