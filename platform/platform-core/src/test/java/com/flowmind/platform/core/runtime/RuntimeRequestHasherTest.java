package com.flowmind.platform.core.runtime;

import com.flowmind.platform.api.enums.AttachmentOwnerTypeEnum;
import com.flowmind.platform.api.request.AttachmentUploadItem;
import com.flowmind.platform.api.request.StartProcessRequest;
import com.flowmind.platform.api.request.SubmitTaskRequest;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class RuntimeRequestHasherTest {

    private final RuntimeRequestHasher hasher = new RuntimeRequestHasher();

    @Test
    void operationIdAndMapInsertionOrderDoNotChangeHash() {
        StartProcessRequest first = startRequest("operation-a", variables("amount", Integer.valueOf(100), "currency", "CNY"));
        StartProcessRequest second = startRequest("operation-b", variables("currency", "CNY", "amount", Integer.valueOf(100)));

        assertEquals(hasher.hash(first), hasher.hash(second));
    }

    @Test
    void attachmentHashUsesContentDigestInsteadOfRawContent() {
        SubmitTaskRequest first = submitRequest("file-content-a".getBytes(StandardCharsets.UTF_8));
        SubmitTaskRequest second = submitRequest("file-content-b".getBytes(StandardCharsets.UTF_8));

        assertNotEquals(hasher.hash(first), hasher.hash(second));
    }

    @Test
    void requestTypeAndNestedVariableValueParticipateInHash() {
        StartProcessRequest start = startRequest("operation-a", variables("approval", variables("level", Integer.valueOf(1))));
        StartProcessRequest changed = startRequest("operation-a", variables("approval", variables("level", Integer.valueOf(2))));

        assertNotEquals(hasher.hash(start), hasher.hash(changed));
        assertNotEquals(hasher.hash(start), hasher.hash(submitRequest("file-content-a".getBytes(StandardCharsets.UTF_8))));
    }

    private StartProcessRequest startRequest(String operationId, Map<String, Object> variables) {
        StartProcessRequest request = new StartProcessRequest();
        request.setOperationId(operationId);
        request.setProcessCode("expense");
        request.setBusinessKey("business-1");
        request.setInstanceTitle("Expense application");
        request.setStarterUserId("user-1");
        request.setStarterDeptId("dept-1");
        request.setVariables(variables);
        return request;
    }

    private SubmitTaskRequest submitRequest(byte[] content) {
        SubmitTaskRequest request = new SubmitTaskRequest();
        request.setOperationId("operation-submit");
        request.setTaskId("task-1");
        request.setExpectedTaskVersion(Long.valueOf(3));
        request.setOperatorUserId("user-1");
        request.setVariables(variables("amount", Integer.valueOf(100)));
        AttachmentUploadItem item = new AttachmentUploadItem();
        item.setAttachmentCode("invoice");
        item.setOwnerType(AttachmentOwnerTypeEnum.TASK);
        item.setFileName("invoice.txt");
        item.setContentType("text/plain");
        item.setSizeBytes(Long.valueOf(content.length));
        item.setContent(content);
        request.setAttachments(Arrays.asList(item));
        return request;
    }

    private Map<String, Object> variables(Object... values) {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        for (int index = 0; index < values.length; index += 2) {
            map.put((String) values[index], values[index + 1]);
        }
        return map;
    }
}
