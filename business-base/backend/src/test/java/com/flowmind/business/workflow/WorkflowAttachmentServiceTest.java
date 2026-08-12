package com.flowmind.business.workflow;

import com.flowmind.business.common.OperationIdFactory;
import com.flowmind.business.platform.PlatformDtoMapper;
import com.flowmind.business.platform.PlatformFacade;
import com.flowmind.business.workflow.dto.WorkflowDetailResponse;
import com.flowmind.platform.api.dto.AttachmentDTO;
import com.flowmind.platform.api.dto.AttachmentDownloadDTO;
import com.flowmind.platform.api.dto.AttachmentQuery;
import com.flowmind.platform.api.dto.UserContext;
import com.flowmind.platform.api.enums.AttachmentOwnerTypeEnum;
import com.flowmind.platform.api.request.DeleteAttachmentRequest;
import com.flowmind.platform.api.request.SaveInstanceAttachmentRequest;
import com.flowmind.platform.api.request.SaveTaskAttachmentRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkflowAttachmentServiceTest {

    @Test
    void uploadInstanceBuildsTrustedPlatformRequest() {
        PlatformFacade facade = facade();
        AttachmentDTO saved = attachment("att-1");
        when(facade.saveInstanceAttachment(any(SaveInstanceAttachmentRequest.class))).thenReturn(saved);
        WorkflowAttachmentService service = service(facade);

        WorkflowDetailResponse.AttachmentView result = service.uploadInstance("instance-1",
                file("../receipt.txt", "text/plain"), "receipt", "bankReceipt",
                "task-1", Long.valueOf(4), "idem-upload");

        ArgumentCaptor<SaveInstanceAttachmentRequest> captor = ArgumentCaptor.forClass(SaveInstanceAttachmentRequest.class);
        verify(facade).saveInstanceAttachment(captor.capture());
        SaveInstanceAttachmentRequest request = captor.getValue();
        assertThat(request.getInstanceId()).isEqualTo("instance-1");
        assertThat(request.getSourceTaskId()).isEqualTo("task-1");
        assertThat(request.getExpectedTaskVersion()).isEqualTo(4L);
        assertThat(request.getOperationId()).isEqualTo(new OperationIdFactory()
                .create("workflow-attachment", "upload-instance", "instance-1", "user-1", "idem-upload"));
        assertThat(request.getAttachment().getOwnerType()).isEqualTo(AttachmentOwnerTypeEnum.INSTANCE);
        assertThat(request.getAttachment().getAttachmentCode()).isEqualTo("receipt");
        assertThat(request.getAttachment().getFieldCode()).isEqualTo("bankReceipt");
        assertThat(request.getAttachment().getFileName()).isEqualTo("receipt.txt");
        assertThat(request.getAttachment().getContent()).isEqualTo("content".getBytes(StandardCharsets.UTF_8));
        assertThat(result.getAttachmentId()).isEqualTo("att-1");
    }

    @Test
    void uploadTaskBuildsTrustedPlatformRequest() {
        PlatformFacade facade = facade();
        when(facade.saveTaskAttachment(any(SaveTaskAttachmentRequest.class))).thenReturn(attachment("att-task"));
        WorkflowAttachmentService service = service(facade);

        service.uploadTask("task-2", file("note.txt", null), "instance-2",
                "note", null, Long.valueOf(8), "idem-task");

        ArgumentCaptor<SaveTaskAttachmentRequest> captor = ArgumentCaptor.forClass(SaveTaskAttachmentRequest.class);
        verify(facade).saveTaskAttachment(captor.capture());
        SaveTaskAttachmentRequest request = captor.getValue();
        assertThat(request.getTaskId()).isEqualTo("task-2");
        assertThat(request.getInstanceId()).isEqualTo("instance-2");
        assertThat(request.getExpectedTaskVersion()).isEqualTo(8L);
        assertThat(request.getOperationId()).isEqualTo(new OperationIdFactory()
                .create("workflow-attachment", "upload-task", "task-2", "user-1", "idem-task"));
        assertThat(request.getAttachment().getOwnerType()).isEqualTo(AttachmentOwnerTypeEnum.TASK);
        assertThat(request.getAttachment().getContentType()).isEqualTo("application/octet-stream");
    }

    @Test
    void queryUsesPlatformFacadeAndRemovesDeletedAttachments() {
        PlatformFacade facade = facade();
        AttachmentDTO visible = attachment("att-1");
        AttachmentDTO deleted = attachment("att-2");
        deleted.setDeleted(Boolean.TRUE);
        when(facade.queryAttachments(any(AttachmentQuery.class))).thenReturn(java.util.Arrays.asList(visible, deleted));
        WorkflowAttachmentService service = service(facade);

        java.util.List<WorkflowDetailResponse.AttachmentView> result =
                service.query("instance-1", "task-1", "receipt", "field-1");

        ArgumentCaptor<AttachmentQuery> captor = ArgumentCaptor.forClass(AttachmentQuery.class);
        verify(facade).queryAttachments(captor.capture());
        assertThat(captor.getValue().getInstanceId()).isEqualTo("instance-1");
        assertThat(captor.getValue().getTaskId()).isEqualTo("task-1");
        assertThat(captor.getValue().getAttachmentCode()).isEqualTo("receipt");
        assertThat(captor.getValue().getFieldCode()).isEqualTo("field-1");
        assertThat(result).extracting(WorkflowDetailResponse.AttachmentView::getAttachmentId)
                .containsExactly("att-1");
    }

    @Test
    void downloadSanitizesFileNameAndDefaultsContentType() {
        PlatformFacade facade = facade();
        AttachmentDTO metadata = attachment("att-1");
        metadata.setFileName("..\\bad\r\nname.pdf");
        metadata.setContentType(null);
        AttachmentDownloadDTO download = new AttachmentDownloadDTO();
        download.setAttachment(metadata);
        download.setContent(new byte[] {1, 2, 3});
        when(facade.downloadAttachment("att-1")).thenReturn(download);
        WorkflowAttachmentService.AttachmentContent result = service(facade).download("att-1");

        assertThat(result.getFileName()).isEqualTo("bad__name.pdf");
        assertThat(result.getContentType()).isEqualTo("application/octet-stream");
        assertThat(result.getContent()).containsExactly(1, 2, 3);
    }

    @Test
    void deleteCreatesStableOperationId() {
        PlatformFacade facade = facade();
        WorkflowAttachmentService service = service(facade);

        service.delete("att-1", "idem-delete");

        ArgumentCaptor<DeleteAttachmentRequest> captor = ArgumentCaptor.forClass(DeleteAttachmentRequest.class);
        verify(facade).deleteAttachment(captor.capture());
        assertThat(captor.getValue().getAttachmentId()).isEqualTo("att-1");
        assertThat(captor.getValue().getOperationId()).isEqualTo(new OperationIdFactory()
                .create("workflow-attachment", "delete", "att-1", "user-1", "idem-delete"));
    }

    private WorkflowAttachmentService service(PlatformFacade facade) {
        return new WorkflowAttachmentService(facade, new PlatformDtoMapper(), new OperationIdFactory());
    }

    private PlatformFacade facade() {
        PlatformFacade facade = mock(PlatformFacade.class);
        when(facade.currentUser()).thenReturn(new UserContext("user-1", "User 1", "dept-1", "Dept 1"));
        when(facade.queryAttachments(any(AttachmentQuery.class))).thenReturn(Collections.<AttachmentDTO>emptyList());
        return facade;
    }

    private MockMultipartFile file(String originalName, String contentType) {
        return new MockMultipartFile("file", originalName, contentType,
                "content".getBytes(StandardCharsets.UTF_8));
    }

    private AttachmentDTO attachment(String id) {
        AttachmentDTO attachment = new AttachmentDTO();
        attachment.setAttachmentId(id);
        attachment.setInstanceId("instance-1");
        attachment.setFileName("receipt.txt");
        attachment.setContentType("text/plain");
        attachment.setSizeBytes(Long.valueOf(7));
        attachment.setDeleted(Boolean.FALSE);
        return attachment;
    }
}
