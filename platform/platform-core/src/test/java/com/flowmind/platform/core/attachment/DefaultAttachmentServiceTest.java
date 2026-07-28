package com.flowmind.platform.core.attachment;

import com.flowmind.platform.api.dto.StoredFile;
import com.flowmind.platform.api.dto.AttachmentDTO;
import com.flowmind.platform.api.dto.AttachmentTemplateCheckResult;
import com.flowmind.platform.api.dto.UserContext;
import com.flowmind.platform.api.enums.AttachmentOwnerTypeEnum;
import com.flowmind.platform.api.request.AttachmentUploadItem;
import com.flowmind.platform.api.request.CheckAttachmentRequest;
import com.flowmind.platform.api.request.DeleteAttachmentRequest;
import com.flowmind.platform.api.request.SaveInstanceAttachmentRequest;
import com.flowmind.platform.api.request.SaveTaskAttachmentRequest;
import com.flowmind.platform.api.spi.CurrentUserProvider;
import com.flowmind.platform.api.spi.FileStorageProvider;
import com.flowmind.platform.core.runtime.RuntimeValidationException;
import com.flowmind.platform.core.runtime.RuntimeStateException;
import com.flowmind.platform.core.runtime.RuntimeOperationExecutor;
import com.flowmind.platform.core.security.AttachmentAccessGuard;
import com.flowmind.platform.core.definition.OperationIdempotencyDecision;
import com.flowmind.platform.core.definition.OperationIdempotencyDecisionType;
import com.flowmind.platform.persistence.entity.ProcessActiveTaskEntity;
import com.flowmind.platform.persistence.entity.ProcessAttachmentEntity;
import com.flowmind.platform.persistence.entity.ProcessAttachmentTemplateEntity;
import com.flowmind.platform.persistence.entity.ProcessDefinitionAttachmentConfigEntity;
import com.flowmind.platform.persistence.entity.ProcessInstanceEntity;
import com.flowmind.platform.persistence.repository.ActiveTaskRepository;
import com.flowmind.platform.persistence.repository.ProcessAttachmentRepository;
import com.flowmind.platform.persistence.repository.ProcessAttachmentTemplateRepository;
import com.flowmind.platform.persistence.repository.ProcessDefinitionAttachmentConfigRepository;
import com.flowmind.platform.persistence.repository.ProcessInstanceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultAttachmentServiceTest {
    @Test
    void deniedUploadNeverCallsFileStorage() {
        Fixture fixture = new Fixture(false);
        assertThrows(RuntimeValidationException.class, () -> fixture.service.saveInstanceAttachment(fixture.request()));
        verify(fixture.storage, never()).store(any());
        verify(fixture.attachments, never()).insertWhenTaskOpenAndWithinLimit(any(), any(), any());
    }

    @Test
    void allowedUploadStoresMetadataAfterTemplateValidation() {
        Fixture fixture = new Fixture(true);
        when(fixture.storage.store(any())).thenReturn(new StoredFile("mock://receipt", "receipt.pdf", "application/pdf", 3L));
        when(fixture.attachments.insertWhenTaskOpenAndWithinLimit(any(), any(), any())).thenReturn(1);

        assertEquals("receipt", fixture.service.saveInstanceAttachment(fixture.request()).getAttachmentCode());
        verify(fixture.storage).store(any());
        verify(fixture.attachments).insertWhenTaskOpenAndWithinLimit(any(), any(), any());
    }

    @Test
    void uploadReplayReturnsStoredResultWithoutWritingFileAgain() {
        RuntimeOperationExecutor operations = mock(RuntimeOperationExecutor.class);
        Fixture fixture = new Fixture(true, operations);
        AttachmentDTO replayed = new AttachmentDTO(); replayed.setAttachmentId("attachment-1");
        when(operations.begin(any(), any(), any(), any(), any(), any())).thenReturn(
                new OperationIdempotencyDecision(OperationIdempotencyDecisionType.REPLAY_SUCCESS, null));
        when(operations.replayResult(any(), org.mockito.ArgumentMatchers.eq(AttachmentDTO.class))).thenReturn(replayed);

        assertEquals("attachment-1", fixture.service.saveInstanceAttachment(fixture.request()).getAttachmentId());
        verify(fixture.storage, never()).store(any());
    }

    @Test
    void taskUploadUsesTaskOwnershipAndWritesMetadata() {
        Fixture fixture = new Fixture(true);
        when(fixture.storage.store(any())).thenReturn(new StoredFile("storage-task", "receipt.pdf", "application/pdf", 3L));
        when(fixture.attachments.insertWhenTaskOpenAndWithinLimit(any(), any(), any())).thenReturn(1);

        assertEquals(AttachmentOwnerTypeEnum.TASK, fixture.service.saveTaskAttachment(fixture.taskRequest()).getOwnerType());
        verify(fixture.storage).store(any());
    }

    @Test
    void invalidUploadInputsFailBeforeStorageIsCalled() {
        Fixture fixture = new Fixture(true);
        SaveInstanceAttachmentRequest request = fixture.request();
        AttachmentUploadItem item = request.getAttachment();
        item.setFileName("receipt.exe");
        assertThrows(RuntimeValidationException.class, () -> fixture.service.saveInstanceAttachment(request));

        item.setFileName("receipt.pdf");
        item.setSizeBytes(4L);
        assertThrows(RuntimeValidationException.class, () -> fixture.service.saveInstanceAttachment(request));

        item.setSizeBytes(3L);
        when(fixture.attachments.countActiveByInstanceAndCode("instance-1", "receipt")).thenReturn(2L);
        assertThrows(RuntimeValidationException.class, () -> fixture.service.saveInstanceAttachment(request));
        verify(fixture.storage, never()).store(any());
    }

    @Test
    void sourceTaskMismatchAndVersionConflictFailBeforeStorageIsCalled() {
        Fixture fixture = new Fixture(true);
        fixture.task.setInstanceId("other-instance");
        assertThrows(RuntimeStateException.class, () -> fixture.service.saveInstanceAttachment(fixture.request()));
        fixture.task.setInstanceId("instance-1");
        SaveInstanceAttachmentRequest staleRequest = fixture.request();
        staleRequest.setExpectedTaskVersion(9L);
        assertThrows(RuntimeStateException.class, () -> fixture.service.saveInstanceAttachment(staleRequest));
        verify(fixture.storage, never()).store(any());
    }

    @Test
    void requiredAttachmentCheckHandlesMissingMinimumMaximumAndNonApplicableNode() {
        Fixture fixture = new Fixture(true);
        CheckAttachmentRequest request = fixture.checkRequest("apply");
        when(fixture.attachments.countActiveByInstanceAndCode("instance-1", "receipt")).thenReturn(0L);
        AttachmentTemplateCheckResult missing = fixture.service.checkRequiredAttachments(request);
        assertEquals(false, missing.isPassed());
        assertEquals(Collections.singletonList("receipt"), missing.getMissingAttachmentCodes());

        when(fixture.attachments.countActiveByInstanceAndCode("instance-1", "receipt")).thenReturn(1L);
        assertEquals(true, fixture.service.checkRequiredAttachments(request).isPassed());
        when(fixture.attachments.countActiveByInstanceAndCode("instance-1", "receipt")).thenReturn(3L);
        assertEquals(false, fixture.service.checkRequiredAttachments(request).isPassed());

        fixture.config.setApplicableNodeCodes("[\"review\"]");
        assertEquals(true, fixture.service.checkRequiredAttachments(request).isPassed());
    }

    @Test
    void deleteSoftDeletesActiveAttachmentAndRetryOnlyCleansStorage() {
        Fixture fixture = new Fixture(true);
        ProcessAttachmentEntity attachment = fixture.attachment(false);
        when(fixture.attachments.findById("attachment-1")).thenReturn(attachment);
        when(fixture.attachments.softDeleteWhenTaskOpen(any(), any(), any(), any(), any())).thenReturn(1);
        fixture.service.deleteAttachment(fixture.deleteRequest());
        verify(fixture.attachments).softDeleteWhenTaskOpen(any(), any(), any(), any(), any());
        verify(fixture.storage).delete("storage-1");

        attachment.setDeleted(Boolean.TRUE);
        fixture.service.deleteAttachment(fixture.deleteRequest());
        verify(fixture.attachments).softDeleteWhenTaskOpen(any(), any(), any(), any(), any());
        verify(fixture.storage, org.mockito.Mockito.times(2)).delete("storage-1");
    }

    @Test
    void storageCleanupFailureDoesNotUndoSoftDelete() {
        Fixture fixture = new Fixture(true);
        when(fixture.attachments.findById("attachment-1")).thenReturn(fixture.attachment(false));
        when(fixture.attachments.softDeleteWhenTaskOpen(any(), any(), any(), any(), any())).thenReturn(1);
        doThrow(new IllegalStateException("storage unavailable")).when(fixture.storage).delete("storage-1");
        fixture.service.deleteAttachment(fixture.deleteRequest());
        verify(fixture.attachments).softDeleteWhenTaskOpen(any(), any(), any(), any(), any());
    }

    @Test
    void deleteReplayRetriesStorageCleanupForAlreadyDeletedAttachment() {
        RuntimeOperationExecutor operations = mock(RuntimeOperationExecutor.class);
        Fixture fixture = new Fixture(true, operations);
        when(operations.begin(any(), any(), any(), any(), any(), any())).thenReturn(
                new OperationIdempotencyDecision(OperationIdempotencyDecisionType.REPLAY_SUCCESS, null));
        when(fixture.attachments.findById("attachment-1")).thenReturn(fixture.attachment(true));

        fixture.service.deleteAttachment(fixture.deleteRequest());

        verify(fixture.storage).delete("storage-1");
        verify(fixture.attachments, never()).softDeleteWhenTaskOpen(any(), any(), any(), any(), any());
    }

    @Test
    void metadataFailureAfterStoreRegistersRollbackCleanup() {
        Fixture fixture = new Fixture(true);
        when(fixture.storage.store(any())).thenReturn(new StoredFile("storage-rollback", "receipt.pdf", "application/pdf", 3L));
        when(fixture.attachments.insertWhenTaskOpenAndWithinLimit(any(), any(), any())).thenReturn(0);
        TransactionSynchronizationManager.initSynchronization();
        try {
            assertThrows(RuntimeStateException.class, () -> fixture.service.saveInstanceAttachment(fixture.request()));
            for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
                synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
            }
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
        verify(fixture.storage).delete("storage-rollback");
    }

    private static final class Fixture {
        private final ProcessAttachmentRepository attachments = mock(ProcessAttachmentRepository.class);
        private final ProcessInstanceRepository instances = mock(ProcessInstanceRepository.class);
        private final ActiveTaskRepository tasks = mock(ActiveTaskRepository.class);
        private final ProcessDefinitionAttachmentConfigRepository configs = mock(ProcessDefinitionAttachmentConfigRepository.class);
        private final ProcessAttachmentTemplateRepository templates = mock(ProcessAttachmentTemplateRepository.class);
        private final FileStorageProvider storage = mock(FileStorageProvider.class);
        private final DefaultAttachmentService service;
        private final ProcessActiveTaskEntity task = new ProcessActiveTaskEntity();
        private final ProcessDefinitionAttachmentConfigEntity config = new ProcessDefinitionAttachmentConfigEntity();

        private Fixture(boolean allowed) {
            this(allowed, null);
        }

        private Fixture(boolean allowed, RuntimeOperationExecutor operations) {
            CurrentUserProvider users = () -> new UserContext("user-1", "User", null, null);
            service = new DefaultAttachmentService(attachments, instances, tasks, configs, templates, storage,
                    new AttachmentAccessGuard(request -> allowed), users, operations);
            ProcessInstanceEntity instance = new ProcessInstanceEntity(); instance.setId("instance-1"); instance.setDefinitionId("definition-1"); instance.setAttachmentConfigId("config-1");
            task.setId("task-1"); task.setInstanceId("instance-1"); task.setNodeCode("apply"); task.setTaskStatus("ACTIVE"); task.setLockVersion(0L);
            config.setAttachmentCode("receipt"); config.setAttachmentTemplateId("template-1"); config.setApplicableNodeCodes("[\"apply\"]"); config.setMinCount(1); config.setMaxCount(2); config.setRequired(Boolean.TRUE);
            ProcessAttachmentTemplateEntity template = new ProcessAttachmentTemplateEntity(); template.setAllowedExtensions("[\"pdf\"]"); template.setMaxSizeBytes(1024L);
            when(instances.findById("instance-1")).thenReturn(instance); when(tasks.findById("task-1")).thenReturn(task);
            when(configs.findByDefinitionIdAndAttachmentConfigId("definition-1", "config-1")).thenReturn(Collections.singletonList(config));
            when(templates.findById("template-1")).thenReturn(Optional.of(template));
            when(attachments.countActiveByInstanceAndCode("instance-1", "receipt")).thenReturn(0L);
        }

        private SaveInstanceAttachmentRequest request() {
            AttachmentUploadItem item = new AttachmentUploadItem(); item.setOwnerType(AttachmentOwnerTypeEnum.INSTANCE); item.setAttachmentCode("receipt"); item.setFileName("receipt.pdf"); item.setContentType("application/pdf"); item.setContent(new byte[] {1, 2, 3}); item.setSizeBytes(3L);
            SaveInstanceAttachmentRequest request = new SaveInstanceAttachmentRequest(); request.setOperationId("op-1"); request.setInstanceId("instance-1"); request.setSourceTaskId("task-1"); request.setExpectedTaskVersion(0L); request.setOperatorUserId("user-1"); request.setAttachment(item); return request;
        }

        private SaveTaskAttachmentRequest taskRequest() {
            AttachmentUploadItem item = request().getAttachment(); item.setOwnerType(AttachmentOwnerTypeEnum.TASK);
            SaveTaskAttachmentRequest request = new SaveTaskAttachmentRequest(); request.setOperationId("op-task-1"); request.setInstanceId("instance-1"); request.setTaskId("task-1"); request.setExpectedTaskVersion(0L); request.setOperatorUserId("user-1"); request.setAttachment(item); return request;
        }

        private CheckAttachmentRequest checkRequest(String nodeCode) {
            CheckAttachmentRequest request = new CheckAttachmentRequest(); request.setInstanceId("instance-1"); request.setNodeCode(nodeCode); request.setOperatorUserId("user-1"); return request;
        }

        private DeleteAttachmentRequest deleteRequest() {
            DeleteAttachmentRequest request = new DeleteAttachmentRequest(); request.setOperationId("op-delete-1"); request.setAttachmentId("attachment-1"); request.setOperatorUserId("user-1"); return request;
        }

        private ProcessAttachmentEntity attachment(boolean deleted) {
            ProcessAttachmentEntity value = new ProcessAttachmentEntity(); value.setId("attachment-1"); value.setInstanceId("instance-1"); value.setTaskId("task-1"); value.setOwnerType("INSTANCE"); value.setStorageKey("storage-1"); value.setDeleted(Boolean.valueOf(deleted)); return value;
        }
    }
}
