package com.flowmind.business.generated.entryapplication;

import com.flowmind.business.generated.entryapplication.dto.EntryApplicationSubmitRequest;
import com.flowmind.business.generated.entryapplication.dto.EntryApplicationSubmitResponse;
import com.flowmind.business.security.CurrentBusinessUserProvider;
import com.flowmind.platform.api.dto.ProcessInstanceDTO;
import com.flowmind.platform.api.dto.TaskDTO;
import com.flowmind.platform.api.enums.AttachmentOwnerTypeEnum;
import com.flowmind.platform.api.enums.InstanceStatusEnum;
import com.flowmind.platform.api.request.AttachmentUploadItem;
import com.flowmind.platform.api.request.StartProcessRequest;
import com.flowmind.platform.api.service.ProcessRuntimeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 入金申请服务单元测试。
 * <p>
 * 平台运行期服务 {@link ProcessRuntimeService} 与受信用户上下文均为 Mock；
 * 受信用户仅在真正到达平台调用路径的用例内打桩（通过 helpers），
 * 绝不在用例之外的公共初始化阶段打桩。
 */
@ExtendWith(MockitoExtension.class)
class EntryApplicationServiceTest {

    @Mock
    private ProcessRuntimeService processRuntimeService;

    @Mock
    private CurrentBusinessUserProvider currentBusinessUserProvider;

    private EntryApplicationService newService() {
        return new EntryApplicationService(processRuntimeService, currentBusinessUserProvider);
    }

    /** 仅由到达平台调用路径的用例调用。 */
    private CurrentBusinessUserProvider.BusinessUser stubCurrentUser(String userId, String departmentId) {
        CurrentBusinessUserProvider.BusinessUser user =
                new CurrentBusinessUserProvider.BusinessUser(userId, departmentId);
        when(currentBusinessUserProvider.currentUser()).thenReturn(user);
        return user;
    }

    /**
     * 返回一个真实的平台实例状态枚举常量，避免猜测枚举常量名；
     * 响应断言统一使用 String.valueOf(status) 与生产映射保持一致。
     */
    private static InstanceStatusEnum anyInstanceStatus() {
        return InstanceStatusEnum.values()[0];
    }

    private static EntryApplicationSubmitRequest validRequest() {
        EntryApplicationSubmitRequest request = new EntryApplicationSubmitRequest();
        request.setApplicantName("张三");
        request.setAmount(new BigDecimal("1000.00"));
        return request;
    }

    private static MockMultipartFile validReceipt() {
        return new MockMultipartFile("bankReceipt", "receipt.pdf", "application/pdf", new byte[] {1, 2, 3});
    }

    private static TaskDTO taskDto(String taskId, String nodeCode, String nodeName) {
        TaskDTO task = mock(TaskDTO.class);
        when(task.getTaskId()).thenReturn(taskId);
        when(task.getNodeCode()).thenReturn(nodeCode);
        when(task.getNodeName()).thenReturn(nodeName);
        return task;
    }

    private static ProcessInstanceDTO instanceDto(String instanceId,
                                                  InstanceStatusEnum status,
                                                  List<TaskDTO> tasks) {
        ProcessInstanceDTO instance = mock(ProcessInstanceDTO.class);
        when(instance.getInstanceId()).thenReturn(instanceId);
        when(instance.getInstanceStatus()).thenReturn(status);
        when(instance.getCreatedTasks()).thenReturn(tasks);
        return instance;
    }

    @Test
    void submit_success_mapsFieldsAttachmentsAndResponse() throws Exception {
        CurrentBusinessUserProvider.BusinessUser user = stubCurrentUser("user-1", "dept-1");
        InstanceStatusEnum status = anyInstanceStatus();
        TaskDTO task = taskDto("task-apply-1", "apply", "提交申请");
        ProcessInstanceDTO instance = instanceDto("inst-1", status, Collections.singletonList(task));
        when(processRuntimeService.startAndSubmit(any(StartProcessRequest.class))).thenReturn(instance);

        EntryApplicationService service = newService();
        EntryApplicationSubmitRequest request = validRequest();
        request.setApplicantName("  张三  ");
        MockMultipartFile receipt1 = validReceipt();
        MockMultipartFile receipt2 =
                new MockMultipartFile("bankReceipt", "receipt-2.png", "image/png", new byte[] {4, 5});

        EntryApplicationSubmitResponse response = service.submit(
                request, new MultipartFile[] {receipt1, receipt2}, "idem-1");

        ArgumentCaptor<StartProcessRequest> captor = ArgumentCaptor.forClass(StartProcessRequest.class);
        verify(processRuntimeService, times(1)).startAndSubmit(captor.capture());
        verify(currentBusinessUserProvider, times(1)).currentUser();

        StartProcessRequest captured = captor.getValue();
        assertEquals("entry_application", captured.getProcessCode());
        assertEquals("入金申请-张三-1000.00", captured.getInstanceTitle());
        assertEquals(user.getUserId(), captured.getStarterUserId());
        assertEquals(user.getDepartmentId(), captured.getStarterDeptId());
        assertEquals("entry_application:user-1:idem-1", captured.getOperationId());

        Map<String, Object> variables = captured.getVariables();
        assertNotNull(variables);
        assertEquals(2, variables.size());
        assertEquals("张三", variables.get("applicantName"));
        assertEquals(new BigDecimal("1000.00"), variables.get("amount"));

        List<AttachmentUploadItem> attachments = captured.getAttachments();
        assertNotNull(attachments);
        assertEquals(2, attachments.size());
        AttachmentUploadItem first = attachments.get(0);
        assertEquals("bankReceipt", first.getAttachmentCode());
        assertEquals(AttachmentOwnerTypeEnum.INSTANCE, first.getOwnerType());
        assertEquals("receipt.pdf", first.getFileName());
        assertEquals("application/pdf", first.getContentType());
        assertEquals(3L, first.getSizeBytes());
        assertArrayEquals(new byte[] {1, 2, 3}, first.getContent());
        assertEquals("bankReceipt", attachments.get(1).getAttachmentCode());
        assertEquals("receipt-2.png", attachments.get(1).getFileName());

        assertEquals("inst-1", response.getInstanceId());
        assertEquals(String.valueOf(status), response.getInstanceStatus());
        assertEquals(1, response.getTasks().size());
        EntryApplicationSubmitResponse.TaskSummary summary = response.getTasks().get(0);
        assertEquals("task-apply-1", summary.getTaskId());
        assertEquals("apply", summary.getNodeCode());
        assertEquals("提交申请", summary.getTaskName());
    }

    @Test
    void submit_requiresIdempotencyKey() throws Exception {
        EntryApplicationService service = newService();
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.submit(validRequest(), new MultipartFile[] {validReceipt()}, null));
        assertTrue(ex.getMessage().contains("Idempotency-Key"));
        verify(processRuntimeService, never()).startAndSubmit(any(StartProcessRequest.class));
        verify(currentBusinessUserProvider, never()).currentUser();
    }

    @Test
    void submit_rejectsBlankIdempotencyKey() throws Exception {
        EntryApplicationService service = newService();
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.submit(validRequest(), new MultipartFile[] {validReceipt()}, "   "));
        assertTrue(ex.getMessage().contains("Idempotency-Key"));
        verify(processRuntimeService, never()).startAndSubmit(any(StartProcessRequest.class));
    }

    @Test
    void submit_rejectsBlankApplicantName() throws Exception {
        EntryApplicationService service = newService();
        EntryApplicationSubmitRequest request = validRequest();
        request.setApplicantName("   ");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.submit(request, new MultipartFile[] {validReceipt()}, "idem-1"));
        assertEquals("请输入申请人姓名", ex.getMessage());
        verify(processRuntimeService, never()).startAndSubmit(any(StartProcessRequest.class));
    }

    @Test
    void submit_rejectsMissingAmount() throws Exception {
        EntryApplicationService service = newService();
        EntryApplicationSubmitRequest request = validRequest();
        request.setAmount(null);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.submit(request, new MultipartFile[] {validReceipt()}, "idem-1"));
        assertEquals("请输入入金金额", ex.getMessage());
        verify(processRuntimeService, never()).startAndSubmit(any(StartProcessRequest.class));
    }

    @Test
    void submit_rejectsAmountBelowMinimum() throws Exception {
        EntryApplicationService service = newService();
        EntryApplicationSubmitRequest request = validRequest();
        request.setAmount(new BigDecimal("0.00"));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.submit(request, new MultipartFile[] {validReceipt()}, "idem-1"));
        assertEquals("入金金额必须大于0", ex.getMessage());
        verify(processRuntimeService, never()).startAndSubmit(any(StartProcessRequest.class));
    }

    @Test
    void submit_rejectsMissingRequiredAttachment() throws Exception {
        EntryApplicationService service = newService();
        IllegalArgumentException nullFiles = assertThrows(IllegalArgumentException.class,
                () -> service.submit(validRequest(), null, "idem-1"));
        assertEquals("付款凭证至少需要上传1个文件", nullFiles.getMessage());

        IllegalArgumentException emptyFiles = assertThrows(IllegalArgumentException.class,
                () -> service.submit(validRequest(), new MultipartFile[0], "idem-1"));
        assertEquals("付款凭证至少需要上传1个文件", emptyFiles.getMessage());

        verify(processRuntimeService, never()).startAndSubmit(any(StartProcessRequest.class));
        verify(currentBusinessUserProvider, never()).currentUser();
    }

    @Test
    void submit_rejectsTooManyAttachments() throws Exception {
        EntryApplicationService service = newService();
        MultipartFile[] files = new MultipartFile[6];
        Arrays.fill(files, validReceipt());
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.submit(validRequest(), files, "idem-1"));
        assertEquals("付款凭证最多上传5个文件", ex.getMessage());
        verify(processRuntimeService, never()).startAndSubmit(any(StartProcessRequest.class));
    }

    @Test
    void submit_rejectsUnsupportedExtension() throws Exception {
        EntryApplicationService service = newService();
        MockMultipartFile badFile =
                new MockMultipartFile("bankReceipt", "receipt.exe", "application/octet-stream", new byte[] {1});
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.submit(validRequest(), new MultipartFile[] {badFile}, "idem-1"));
        assertEquals("付款凭证仅支持 pdf、jpg、png 格式", ex.getMessage());
        verify(processRuntimeService, never()).startAndSubmit(any(StartProcessRequest.class));
    }

    @Test
    void submit_rejectsOversizedAttachment() throws Exception {
        EntryApplicationService service = newService();
        MultipartFile oversized = mock(MultipartFile.class);
        when(oversized.getOriginalFilename()).thenReturn("receipt.pdf");
        when(oversized.getSize()).thenReturn(10L * 1024 * 1024 + 1);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.submit(validRequest(), new MultipartFile[] {oversized}, "idem-1"));
        assertEquals("付款凭证单个文件不能超过10MB", ex.getMessage());
        verify(processRuntimeService, never()).startAndSubmit(any(StartProcessRequest.class));
    }

    @Test
    void submit_operationIdIsStableForSameUserAndKey() throws Exception {
        stubCurrentUser("user-1", "dept-1");
        ProcessInstanceDTO instance =
                instanceDto("inst-1", anyInstanceStatus(), Collections.<TaskDTO>emptyList());
        when(processRuntimeService.startAndSubmit(any(StartProcessRequest.class))).thenReturn(instance);

        EntryApplicationService service = newService();
        MockMultipartFile receipt = validReceipt();
        service.submit(validRequest(), new MultipartFile[] {receipt}, "idem-1");
        service.submit(validRequest(), new MultipartFile[] {receipt}, "idem-1");

        ArgumentCaptor<StartProcessRequest> captor = ArgumentCaptor.forClass(StartProcessRequest.class);
        verify(processRuntimeService, times(2)).startAndSubmit(captor.capture());
        List<StartProcessRequest> calls = captor.getAllValues();
        assertEquals("entry_application:user-1:idem-1", calls.get(0).getOperationId());
        assertEquals(calls.get(0).getOperationId(), calls.get(1).getOperationId());
    }

    @Test
    void submit_operationIdDiffersAcrossIdempotencyKeys() throws Exception {
        stubCurrentUser("user-1", "dept-1");
        ProcessInstanceDTO instance =
                instanceDto("inst-1", anyInstanceStatus(), Collections.<TaskDTO>emptyList());
        when(processRuntimeService.startAndSubmit(any(StartProcessRequest.class))).thenReturn(instance);

        EntryApplicationService service = newService();
        MockMultipartFile receipt = validReceipt();
        service.submit(validRequest(), new MultipartFile[] {receipt}, "idem-1");
        service.submit(validRequest(), new MultipartFile[] {receipt}, "idem-2");

        ArgumentCaptor<StartProcessRequest> captor = ArgumentCaptor.forClass(StartProcessRequest.class);
        verify(processRuntimeService, times(2)).startAndSubmit(captor.capture());
        List<StartProcessRequest> calls = captor.getAllValues();
        assertNotEquals(calls.get(0).getOperationId(), calls.get(1).getOperationId());
    }

    @Test
    void submit_mapsEveryCreatedTaskToSummary() throws Exception {
        stubCurrentUser("user-1", "dept-1");
        TaskDTO applyTask = taskDto("task-apply-1", "apply", "提交申请");
        TaskDTO gatewayTask = taskDto("task-gateway-1", "gateway", "金额判断");
        ProcessInstanceDTO instance = instanceDto(
                "inst-1", anyInstanceStatus(), Arrays.asList(applyTask, gatewayTask));
        when(processRuntimeService.startAndSubmit(any(StartProcessRequest.class))).thenReturn(instance);

        EntryApplicationService service = newService();
        EntryApplicationSubmitResponse response =
                service.submit(validRequest(), new MultipartFile[] {validReceipt()}, "idem-1");

        assertEquals(2, response.getTasks().size());
        EntryApplicationSubmitResponse.TaskSummary first = response.getTasks().get(0);
        assertEquals("task-apply-1", first.getTaskId());
        assertEquals("apply", first.getNodeCode());
        assertEquals("提交申请", first.getTaskName());
        EntryApplicationSubmitResponse.TaskSummary second = response.getTasks().get(1);
        assertEquals("task-gateway-1", second.getTaskId());
        assertEquals("gateway", second.getNodeCode());
        assertEquals("金额判断", second.getTaskName());
    }
}
