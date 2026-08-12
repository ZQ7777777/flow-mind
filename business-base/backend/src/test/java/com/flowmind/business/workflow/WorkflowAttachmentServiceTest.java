package com.flowmind.business.workflow;

import com.flowmind.business.common.OperationIdFactory;
import com.flowmind.business.platform.PlatformDtoMapper;
import com.flowmind.business.platform.PlatformFacade;
import com.flowmind.business.workflow.dto.WorkflowDetailResponse;
import com.flowmind.platform.api.dto.AttachmentDTO;
import com.flowmind.platform.api.dto.AttachmentDownloadDTO;
import com.flowmind.platform.api.dto.AttachmentQuery;
import com.flowmind.platform.api.dto.ProcessInstanceDetailDTO;
import com.flowmind.platform.api.dto.UserContext;
import com.flowmind.platform.api.enums.AttachmentOwnerTypeEnum;
import com.flowmind.platform.api.request.ReplaceInstanceAttachmentRequest;
import com.flowmind.platform.api.request.SaveInstanceAttachmentRequest;
import com.flowmind.platform.api.request.SaveTaskAttachmentRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkflowAttachmentServiceTest {
    @Test
    void replacesExistingInstanceAttachmentWithTrustedTaskAndUserContext() {
        PlatformFacade facade = mock(PlatformFacade.class);
        WorkflowQueryService queryService = mock(WorkflowQueryService.class);
        WorkflowAttachmentService service = new WorkflowAttachmentService(facade, new PlatformDtoMapper(),
                new OperationIdFactory(), queryService);
        ProcessInstanceDetailDTO instance = new ProcessInstanceDetailDTO(); instance.setInstanceId("instance-1");
        when(queryService.authorizedTaskInstance("apply-task")).thenReturn(instance);
        when(facade.currentUser()).thenReturn(new UserContext("sales-1", "Sales", null, null));
        AttachmentDTO old = attachment("old-att", AttachmentOwnerTypeEnum.INSTANCE);
        when(facade.attachments("instance-1")).thenReturn(Collections.singletonList(old));
        AttachmentDTO saved = attachment("new-att", AttachmentOwnerTypeEnum.INSTANCE);
        when(facade.replaceInstanceAttachment(any(ReplaceInstanceAttachmentRequest.class))).thenReturn(saved);

        service.replace("apply-task", "old-att", 4L,
                new MockMultipartFile("file", "..\\new.pdf", "application/pdf", "new".getBytes()), "key-1");

        ArgumentCaptor<ReplaceInstanceAttachmentRequest> captor =
                ArgumentCaptor.forClass(ReplaceInstanceAttachmentRequest.class);
        verify(facade).replaceInstanceAttachment(captor.capture());
        assertThat(captor.getValue().getInstanceId()).isEqualTo("instance-1");
        assertThat(captor.getValue().getSourceTaskId()).isEqualTo("apply-task");
        assertThat(captor.getValue().getExpectedTaskVersion()).isEqualTo(4L);
        assertThat(captor.getValue().getOperatorUserId()).isEqualTo("sales-1");
        assertThat(captor.getValue().getAttachment().getAttachmentCode()).isEqualTo("receipt");
        assertThat(captor.getValue().getAttachment().getFileName()).doesNotContain("\\");
    }

    @Test
    void rejectsTaskAttachmentsAndMissingFilesBeforePlatformReplacement() {
        PlatformFacade facade = mock(PlatformFacade.class);
        WorkflowQueryService queryService = mock(WorkflowQueryService.class);
        WorkflowAttachmentService service = new WorkflowAttachmentService(facade, new PlatformDtoMapper(),
                new OperationIdFactory(), queryService);
        ProcessInstanceDetailDTO instance = new ProcessInstanceDetailDTO(); instance.setInstanceId("instance-1");
        when(queryService.authorizedTaskInstance("apply-task")).thenReturn(instance);
        when(facade.attachments("instance-1")).thenReturn(Collections.singletonList(
                attachment("task-att", AttachmentOwnerTypeEnum.TASK)));

        assertThatThrownBy(() -> service.replace("apply-task", "task-att", 1L,
                new MockMultipartFile("file", "task.txt", "text/plain", "x".getBytes()), "key"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("实例附件");
        assertThatThrownBy(() -> service.replace("apply-task", "task-att", 1L,
                new MockMultipartFile("file", new byte[0]), "key"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("不能为空");
    }

    @Test
    void uploadsInstanceAndTaskAttachmentsWithTrustedRequests() {
        PlatformFacade facade = facade();
        when(facade.saveInstanceAttachment(any(SaveInstanceAttachmentRequest.class)))
                .thenReturn(attachment("instance-att", AttachmentOwnerTypeEnum.INSTANCE));
        when(facade.saveTaskAttachment(any(SaveTaskAttachmentRequest.class)))
                .thenReturn(attachment("task-att", AttachmentOwnerTypeEnum.TASK));
        WorkflowAttachmentService service = service(facade);

        WorkflowDetailResponse.AttachmentView instanceResult = service.uploadInstance("instance-1",
                new MockMultipartFile("file", "../receipt.txt", "text/plain", "content".getBytes()),
                "receipt", "proof", "task-1", 4L, "instance-key");
        service.uploadTask("task-2",
                new MockMultipartFile("file", "note.txt", null, "content".getBytes()),
                "instance-1", "note", null, 8L, "task-key");

        ArgumentCaptor<SaveInstanceAttachmentRequest> instanceCaptor =
                ArgumentCaptor.forClass(SaveInstanceAttachmentRequest.class);
        verify(facade).saveInstanceAttachment(instanceCaptor.capture());
        assertThat(instanceCaptor.getValue().getSourceTaskId()).isEqualTo("task-1");
        assertThat(instanceCaptor.getValue().getAttachment().getFileName()).isEqualTo("receipt.txt");
        assertThat(instanceResult.getAttachmentId()).isEqualTo("instance-att");

        ArgumentCaptor<SaveTaskAttachmentRequest> taskCaptor =
                ArgumentCaptor.forClass(SaveTaskAttachmentRequest.class);
        verify(facade).saveTaskAttachment(taskCaptor.capture());
        assertThat(taskCaptor.getValue().getTaskId()).isEqualTo("task-2");
        assertThat(taskCaptor.getValue().getAttachment().getContentType())
                .isEqualTo("application/octet-stream");
    }

    @Test
    void queriesDownloadsAndDeletesThroughTrustedFacade() {
        PlatformFacade facade = facade();
        AttachmentDTO visible = attachment("visible", AttachmentOwnerTypeEnum.INSTANCE);
        AttachmentDTO deleted = attachment("deleted", AttachmentOwnerTypeEnum.INSTANCE);
        deleted.setDeleted(Boolean.TRUE);
        when(facade.queryAttachments(any(AttachmentQuery.class)))
                .thenReturn(java.util.Arrays.asList(visible, deleted));
        AttachmentDTO metadata = attachment("visible", AttachmentOwnerTypeEnum.INSTANCE);
        metadata.setFileName("..\\bad\r\nname.pdf"); metadata.setContentType(null);
        AttachmentDownloadDTO download = new AttachmentDownloadDTO();
        download.setAttachment(metadata); download.setContent(new byte[] {1, 2, 3});
        when(facade.downloadAttachment("visible")).thenReturn(download);
        WorkflowAttachmentService service = service(facade);

        assertThat(service.query("instance-1", null, null, null))
                .extracting(WorkflowDetailResponse.AttachmentView::getAttachmentId)
                .containsExactly("visible");
        WorkflowAttachmentService.AttachmentContent content = service.download("visible");
        assertThat(content.getFileName()).isEqualTo("bad__name.pdf");
        assertThat(content.getContentType()).isEqualTo("application/octet-stream");
        service.delete("visible", "delete-key");
        verify(facade).deleteAttachment(any(com.flowmind.platform.api.request.DeleteAttachmentRequest.class));
    }

    private PlatformFacade facade() {
        PlatformFacade facade = mock(PlatformFacade.class);
        when(facade.currentUser()).thenReturn(new UserContext("user-1", "User", null, null));
        return facade;
    }

    private WorkflowAttachmentService service(PlatformFacade facade) {
        return new WorkflowAttachmentService(facade, new PlatformDtoMapper(), new OperationIdFactory(),
                mock(WorkflowQueryService.class));
    }

    private AttachmentDTO attachment(String id, AttachmentOwnerTypeEnum ownerType) {
        AttachmentDTO attachment = new AttachmentDTO(); attachment.setAttachmentId(id);
        attachment.setInstanceId("instance-1"); attachment.setOwnerType(ownerType);
        attachment.setAttachmentCode("receipt"); attachment.setFieldCode("proof");
        attachment.setFileName("old.pdf"); attachment.setDeleted(Boolean.FALSE); return attachment;
    }
}
