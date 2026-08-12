package com.flowmind.business.workflow;

import com.flowmind.business.common.OperationIdFactory;
import com.flowmind.business.platform.PlatformDtoMapper;
import com.flowmind.business.platform.PlatformFacade;
import com.flowmind.platform.api.dto.AttachmentDTO;
import com.flowmind.platform.api.dto.ProcessInstanceDetailDTO;
import com.flowmind.platform.api.dto.UserContext;
import com.flowmind.platform.api.enums.AttachmentOwnerTypeEnum;
import com.flowmind.platform.api.request.ReplaceInstanceAttachmentRequest;
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

    private AttachmentDTO attachment(String id, AttachmentOwnerTypeEnum ownerType) {
        AttachmentDTO attachment = new AttachmentDTO(); attachment.setAttachmentId(id);
        attachment.setInstanceId("instance-1"); attachment.setOwnerType(ownerType);
        attachment.setAttachmentCode("receipt"); attachment.setFieldCode("proof");
        attachment.setFileName("old.pdf"); attachment.setDeleted(Boolean.FALSE); return attachment;
    }
}
