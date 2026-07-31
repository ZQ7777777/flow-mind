package com.flowmind.platform.web;

import com.flowmind.platform.api.dto.AttachmentDTO;
import com.flowmind.platform.api.dto.AttachmentQuery;
import com.flowmind.platform.api.request.DeleteAttachmentRequest;
import com.flowmind.platform.api.request.DownloadAttachmentRequest;
import com.flowmind.platform.api.request.ReplaceInstanceAttachmentRequest;
import com.flowmind.platform.api.request.SaveInstanceAttachmentRequest;
import com.flowmind.platform.api.request.SaveTaskAttachmentRequest;
import com.flowmind.platform.api.service.AttachmentService;
import com.flowmind.platform.core.runtime.RuntimeErrorCodes;
import com.flowmind.platform.core.runtime.RuntimeStateException;
import com.flowmind.platform.core.runtime.RuntimeValidationException;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AttachmentControllerTest {

    @Test
    void attachmentEndpointsUsePathIdentifiersAndDelegateToSingleService() {
        AttachmentService service = mock(AttachmentService.class);
        AttachmentController controller = new AttachmentController(service);
        SaveInstanceAttachmentRequest instanceRequest = new SaveInstanceAttachmentRequest();
        SaveTaskAttachmentRequest taskRequest = new SaveTaskAttachmentRequest();
        ReplaceInstanceAttachmentRequest replaceRequest = new ReplaceInstanceAttachmentRequest();
        DownloadAttachmentRequest downloadRequest = new DownloadAttachmentRequest();
        DeleteAttachmentRequest deleteRequest = new DeleteAttachmentRequest();
        AttachmentQuery query = new AttachmentQuery();
        AttachmentDTO attachment = new AttachmentDTO(); attachment.setAttachmentId("attachment-1");
        when(service.saveInstanceAttachment(instanceRequest)).thenReturn(attachment);
        when(service.saveTaskAttachment(taskRequest)).thenReturn(attachment);
        when(service.replaceInstanceAttachment(replaceRequest)).thenReturn(attachment);
        when(service.queryAttachments(query)).thenReturn(Collections.singletonList(attachment));

        assertEquals("attachment-1", controller.saveInstance("instance-1", instanceRequest).getAttachmentId());
        assertEquals("attachment-1", controller.saveTask("task-1", taskRequest).getAttachmentId());
        assertEquals("attachment-1",
                controller.replaceInstance("instance-1", "attachment-1", replaceRequest).getAttachmentId());
        assertEquals(1, controller.query(query).size());
        controller.download("attachment-1", downloadRequest);
        controller.delete("attachment-1", deleteRequest);

        assertEquals("instance-1", instanceRequest.getInstanceId());
        assertEquals("task-1", taskRequest.getTaskId());
        assertEquals("instance-1", replaceRequest.getInstanceId());
        assertEquals("attachment-1", replaceRequest.getAttachmentId());
        assertEquals("attachment-1", downloadRequest.getAttachmentId());
        assertEquals("attachment-1", deleteRequest.getAttachmentId());
        verify(service).downloadAttachment(downloadRequest);
        verify(service).deleteAttachment(deleteRequest);
    }

    @Test
    void conflictingPathAndBodyIdentifiersAreRejectedBeforeServiceCall() {
        AttachmentService service = mock(AttachmentService.class);
        AttachmentController controller = new AttachmentController(service);
        SaveInstanceAttachmentRequest request = new SaveInstanceAttachmentRequest();
        request.setInstanceId("other-instance");

        assertThrows(RuntimeValidationException.class, () -> controller.saveInstance("instance-1", request));
    }

    @Test
    void attachmentExceptionsUseDocumentedHttpStatuses() throws Exception {
        AttachmentService service = mock(AttachmentService.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new AttachmentController(service))
                .setControllerAdvice(new PlatformExceptionHandler()).build();
        when(service.saveInstanceAttachment(any())).thenThrow(new RuntimeValidationException(
                RuntimeErrorCodes.ATTACHMENT_PERMISSION_DENIED, "denied"));
        when(service.downloadAttachment(any())).thenThrow(new RuntimeStateException(
                RuntimeErrorCodes.ATTACHMENT_NOT_FOUND, "missing"));
        doThrow(new RuntimeStateException(RuntimeErrorCodes.ATTACHMENT_SOURCE_TASK_INVALID, "closed"))
                .when(service).deleteAttachment(any());
        when(service.replaceInstanceAttachment(any())).thenThrow(new RuntimeStateException(
                RuntimeErrorCodes.ATTACHMENT_SOURCE_TASK_INVALID, "stale"));

        mvc.perform(post("/api/platform/instances/instance-1/attachments")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/platform/instances/instance-1/attachments")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"instanceId\":\"other\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/platform/attachments/attachment-1/download"))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/platform/attachments/attachment-1")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isConflict());
        mvc.perform(put("/api/platform/instances/instance-1/attachments/attachment-1")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isConflict());
        doThrow(new RuntimeStateException(RuntimeErrorCodes.ATTACHMENT_STORAGE_FAILED, "storage failed"))
                .when(service).downloadAttachment(any());
        mvc.perform(get("/api/platform/attachments/attachment-2/download"))
                .andExpect(status().isBadGateway());
    }
}
