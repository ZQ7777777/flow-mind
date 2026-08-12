package com.flowmind.business.generated.entryapplication;

import com.flowmind.business.generated.entryapplication.dto.EntryApplicationSubmitRequest;
import com.flowmind.business.generated.entryapplication.dto.EntryApplicationSubmitResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 入金申请控制器单元测试。
 * <p>
 * 目标后端没有 Spring Boot 启动类，因此本测试不依赖 Spring 上下文测试注解，
 * 而是通过 {@code MockMvcBuilders.standaloneSetup} 显式构造控制器并构建 MockMvc，
 * 仅使用 JUnit 与 Mockito 完成协议层验证。
 */
@ExtendWith(MockitoExtension.class)
class EntryApplicationControllerTest {

    @Mock
    private EntryApplicationService entryApplicationService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        EntryApplicationController controller = new EntryApplicationController(entryApplicationService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    private static MockMultipartFile payloadPart(String json) {
        return new MockMultipartFile("payload", "payload.json", "application/json",
                json.getBytes(StandardCharsets.UTF_8));
    }

    private static MockMultipartFile bankReceiptPart() {
        return new MockMultipartFile("bankReceipt", "receipt.pdf", "application/pdf", new byte[] {1, 2, 3});
    }

    @Test
    void submit_success_returnsInstanceAndTaskSummaries() throws Exception {
        EntryApplicationSubmitResponse response = new EntryApplicationSubmitResponse();
        response.setInstanceId("inst-1");
        response.setInstanceStatus("RUNNING");
        EntryApplicationSubmitResponse.TaskSummary summary = new EntryApplicationSubmitResponse.TaskSummary();
        summary.setTaskId("task-apply-1");
        summary.setNodeCode("apply");
        summary.setTaskName("提交申请");
        response.setTasks(Collections.singletonList(summary));
        when(entryApplicationService.submit(any(EntryApplicationSubmitRequest.class), any(), eq("idem-1")))
                .thenReturn(response);

        mockMvc.perform(multipart(EntryApplicationController.SUBMIT_PATH)
                        .file(payloadPart("{\"applicantName\":\"张三\",\"amount\":1000}"))
                        .file(bankReceiptPart())
                        .header("Idempotency-Key", "idem-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instanceId").value("inst-1"))
                .andExpect(jsonPath("$.instanceStatus").value("RUNNING"))
                .andExpect(jsonPath("$.tasks[0].taskId").value("task-apply-1"))
                .andExpect(jsonPath("$.tasks[0].nodeCode").value("apply"))
                .andExpect(jsonPath("$.tasks[0].taskName").value("提交申请"));

        ArgumentCaptor<EntryApplicationSubmitRequest> requestCaptor =
                ArgumentCaptor.forClass(EntryApplicationSubmitRequest.class);
        ArgumentCaptor<MultipartFile[]> filesCaptor = ArgumentCaptor.forClass(MultipartFile[].class);
        verify(entryApplicationService).submit(requestCaptor.capture(), filesCaptor.capture(), eq("idem-1"));
        assertEquals("张三", requestCaptor.getValue().getApplicantName());
        assertEquals(0, new BigDecimal("1000").compareTo(requestCaptor.getValue().getAmount()));
        assertEquals(1, filesCaptor.getValue().length);
        assertEquals("receipt.pdf", filesCaptor.getValue()[0].getOriginalFilename());
    }

    @Test
    void submit_missingIdempotencyKey_returns400WithErrorBody() throws Exception {
        when(entryApplicationService.submit(any(EntryApplicationSubmitRequest.class), any(), isNull()))
                .thenThrow(new IllegalArgumentException("缺少 Idempotency-Key 请求头"));

        mockMvc.perform(multipart(EntryApplicationController.SUBMIT_PATH)
                        .file(payloadPart("{\"applicantName\":\"张三\",\"amount\":1000}"))
                        .file(bankReceiptPart()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("缺少 Idempotency-Key 请求头"));
    }

    @Test
    void submit_validationError_returns400WithErrorBody() throws Exception {
        when(entryApplicationService.submit(any(EntryApplicationSubmitRequest.class), any(), anyString()))
                .thenThrow(new IllegalArgumentException("付款凭证至少需要上传1个文件"));

        mockMvc.perform(multipart(EntryApplicationController.SUBMIT_PATH)
                        .file(payloadPart("{\"applicantName\":\"张三\",\"amount\":1000}"))
                        .file(bankReceiptPart())
                        .header("Idempotency-Key", "idem-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("付款凭证至少需要上传1个文件"));
    }

    @Test
    void submit_fileReadFailure_returns500WithErrorBody() throws Exception {
        when(entryApplicationService.submit(any(EntryApplicationSubmitRequest.class), any(), anyString()))
                .thenThrow(new IOException("read failed"));

        mockMvc.perform(multipart(EntryApplicationController.SUBMIT_PATH)
                        .file(payloadPart("{\"applicantName\":\"张三\",\"amount\":1000}"))
                        .file(bankReceiptPart())
                        .header("Idempotency-Key", "idem-1"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("FILE_READ_ERROR"));
    }

    @Test
    void submit_missingPayloadPart_returns400() throws Exception {
        mockMvc.perform(multipart(EntryApplicationController.SUBMIT_PATH)
                        .file(bankReceiptPart())
                        .header("Idempotency-Key", "idem-1"))
                .andExpect(status().isBadRequest());

        verify(entryApplicationService, never())
                .submit(any(EntryApplicationSubmitRequest.class), any(), anyString());
    }

    @Test
    void submit_malformedPayload_returns400() throws Exception {
        mockMvc.perform(multipart(EntryApplicationController.SUBMIT_PATH)
                        .file(payloadPart("{not-json"))
                        .file(bankReceiptPart())
                        .header("Idempotency-Key", "idem-1"))
                .andExpect(status().isBadRequest());

        verify(entryApplicationService, never())
                .submit(any(EntryApplicationSubmitRequest.class), any(), anyString());
    }
}
