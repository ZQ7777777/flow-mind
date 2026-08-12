package com.flowmind.business.workflow;

import com.flowmind.business.common.BusinessExceptionHandler;
import com.flowmind.business.common.RequestIdFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WorkflowAttachmentControllerTest {
    private WorkflowAttachmentService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(WorkflowAttachmentService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new WorkflowAttachmentController(service))
                .setControllerAdvice(new BusinessExceptionHandler()).addFilters(new RequestIdFilter()).build();
    }

    @Test
    void mapsInstanceMultipartWithoutCallerControlledUser() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "receipt.txt",
                "text/plain", "content".getBytes("UTF-8"));

        mockMvc.perform(multipart("/api/workflow/instances/instance-1/attachments")
                        .file(file)
                        .header("Idempotency-Key", "idem-1")
                        .param("attachmentCode", "receipt")
                        .param("fieldCode", "bankReceipt")
                        .param("sourceTaskId", "task-1")
                        .param("expectedTaskVersion", "3"))
                .andExpect(status().isOk());

        verify(service).uploadInstance(eq("instance-1"), eq(file), eq("receipt"), eq("bankReceipt"),
                eq("task-1"), eq(Long.valueOf(3)), eq("idem-1"));
    }

    @Test
    void mapsTaskMultipart() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "note.txt",
                "text/plain", "content".getBytes("UTF-8"));

        mockMvc.perform(multipart("/api/workflow/tasks/task-2/attachments")
                        .file(file)
                        .header("Idempotency-Key", "idem-2")
                        .param("instanceId", "instance-2")
                        .param("attachmentCode", "note")
                        .param("expectedTaskVersion", "5"))
                .andExpect(status().isOk());

        verify(service).uploadTask(eq("task-2"), eq(file), eq("instance-2"), eq("note"),
                eq(null), eq(Long.valueOf(5)), eq("idem-2"));
    }

    @Test
    void rejectsMissingIdempotencyKeyOnDelete() throws Exception {
        mockMvc.perform(delete("/api/workflow/attachments/att-1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void downloadSetsContentHeaders() throws Exception {
        when(service.download("att-1")).thenReturn(new WorkflowAttachmentService.AttachmentContent(
                "receipt.pdf", "application/pdf", new byte[] {1, 2, 3}));

        mockMvc.perform(get("/api/workflow/attachments/att-1/content").accept(MediaType.APPLICATION_OCTET_STREAM))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("receipt.pdf")));
    }
}
