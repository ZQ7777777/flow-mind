package com.flowmind.business.generated.entryapplication;

import com.flowmind.business.generated.entryapplication.dto.EntryApplicationSubmitRequest;
import com.flowmind.business.generated.entryapplication.dto.EntryApplicationSubmitResponse;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * 入金申请 HTTP 适配层。
 * <p>
 * 仅负责协议适配（multipart/form-data）、参数提取与响应包装；
 * 状态流转、校验与平台调用全部委托给 {@link EntryApplicationService}。
 */
@RestController
public class EntryApplicationController {

    static final String SUBMIT_PATH = "/api/generated/entry-application/submit";

    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private final EntryApplicationService entryApplicationService;

    public EntryApplicationController(EntryApplicationService entryApplicationService) {
        this.entryApplicationService = entryApplicationService;
    }

    /**
     * 提交入金申请。
     * <p>
     * multipart 结构：名为 {@code payload} 的 JSON 数据部分 + 以附件编码
     * {@code bankReceipt} 命名的文件部分（可多个）。
     *
     * @param idempotencyKey   幂等键请求头（必填，缺失时由服务层校验并返回 400）
     * @param request          表单字段
     * @param bankReceiptFiles 付款凭证文件
     */
    @PostMapping(value = SUBMIT_PATH,
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> submit(
            @RequestHeader(value = IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey,
            @RequestPart("payload") EntryApplicationSubmitRequest request,
            @RequestPart(value = "bankReceipt", required = false) MultipartFile[] bankReceiptFiles) {
        try {
            EntryApplicationSubmitResponse response =
                    entryApplicationService.submit(request, bankReceiptFiles, idempotencyKey);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ErrorBody("VALIDATION_ERROR", e.getMessage()));
        } catch (IOException e) {
            return ResponseEntity.status(500).body(new ErrorBody("FILE_READ_ERROR", "读取上传文件失败"));
        }
    }

    /** 稳定错误契约体：错误码 + 消息。 */
    public static class ErrorBody {

        private final String code;
        private final String message;

        public ErrorBody(String code, String message) {
            this.code = code;
            this.message = message;
        }

        public String getCode() {
            return code;
        }

        public String getMessage() {
            return message;
        }
    }
}
