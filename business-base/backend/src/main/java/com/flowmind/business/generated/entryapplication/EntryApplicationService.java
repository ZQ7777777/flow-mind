package com.flowmind.business.generated.entryapplication;

import com.flowmind.business.generated.entryapplication.dto.EntryApplicationSubmitRequest;
import com.flowmind.business.generated.entryapplication.dto.EntryApplicationSubmitResponse;
import com.flowmind.business.security.CurrentBusinessUserProvider;
import com.flowmind.platform.api.dto.ProcessInstanceDTO;
import com.flowmind.platform.api.dto.TaskDTO;
import com.flowmind.platform.api.enums.AttachmentOwnerTypeEnum;
import com.flowmind.platform.api.request.AttachmentUploadItem;
import com.flowmind.platform.api.request.StartProcessRequest;
import com.flowmind.platform.api.service.ProcessRuntimeService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 入金申请业务服务。
 * <p>
 * 设计约束：
 * <ul>
 *   <li>流程编码使用已确认字面量 {@value #PROCESS_CODE}，不接收客户端传入；</li>
 *   <li>starterUserId / starterDeptId 只来自 {@link CurrentBusinessUserProvider#currentUser()}；</li>
 *   <li>唯一平台运行期调用是 {@code ProcessRuntimeService#startAndSubmit(StartProcessRequest)}，且仅调用一次；</li>
 *   <li>operationId 由流程编码 + 受信用户 ID + Idempotency-Key 稳定派生，保证同一用户同一幂等键重试时保持一致；</li>
 *   <li>发起页附件仅包含 apply 节点的付款凭证（bankReceipt）。</li>
 * </ul>
 */
@Service
public class EntryApplicationService {

    /** 已确认的流程编码字面量。 */
    static final String PROCESS_CODE = "entry_application";

    /** 发起节点附件编码：付款凭证。 */
    static final String ATTACHMENT_CODE_BANK_RECEIPT = "bankReceipt";
    private static final int BANK_RECEIPT_MIN_COUNT = 1;
    private static final int BANK_RECEIPT_MAX_COUNT = 5;
    private static final long BANK_RECEIPT_MAX_SIZE_BYTES = 10L * 1024 * 1024;
    private static final List<String> BANK_RECEIPT_ALLOWED_EXTENSIONS =
            Collections.unmodifiableList(Arrays.asList("pdf", "jpg", "png"));

    private static final BigDecimal MIN_AMOUNT = new BigDecimal("0.01");

    private final ProcessRuntimeService processRuntimeService;
    private final CurrentBusinessUserProvider currentBusinessUserProvider;

    public EntryApplicationService(ProcessRuntimeService processRuntimeService,
                                   CurrentBusinessUserProvider currentBusinessUserProvider) {
        this.processRuntimeService = processRuntimeService;
        this.currentBusinessUserProvider = currentBusinessUserProvider;
    }

    /**
     * 提交入金申请：校验表单与附件规则，组装平台请求并调用
     * {@code ProcessRuntimeService#startAndSubmit(StartProcessRequest)} 一次。
     *
     * @param request          表单字段（applicantName / amount）
     * @param bankReceiptFiles 付款凭证文件（multipart part 名称为附件编码 bankReceipt）
     * @param idempotencyKey   必填的幂等键
     * @return 实例 ID、实例状态与创建出的下一步任务摘要
     */
    public EntryApplicationSubmitResponse submit(EntryApplicationSubmitRequest request,
                                                 MultipartFile[] bankReceiptFiles,
                                                 String idempotencyKey) throws IOException {
        if (idempotencyKey == null || idempotencyKey.trim().isEmpty()) {
            throw new IllegalArgumentException("缺少 Idempotency-Key 请求头");
        }
        validateFields(request);
        List<AttachmentUploadItem> attachments = buildBankReceiptAttachments(bankReceiptFiles);

        CurrentBusinessUserProvider.BusinessUser user = currentBusinessUserProvider.currentUser();
        String applicantName = request.getApplicantName().trim();

        // 仅将确认的表单字段按精确 fieldCode 写入流程变量。
        Map<String, Object> variables = new LinkedHashMap<String, Object>();
        variables.put("applicantName", applicantName);
        variables.put("amount", request.getAmount());

        StartProcessRequest startRequest = new StartProcessRequest();
        startRequest.setProcessCode(PROCESS_CODE);
        startRequest.setInstanceTitle("入金申请-" + applicantName + "-" + request.getAmount().toPlainString());
        startRequest.setStarterUserId(user.getUserId());
        startRequest.setStarterDeptId(user.getDepartmentId());
        startRequest.setOperationId(buildOperationId(user.getUserId(), idempotencyKey));
        startRequest.setVariables(variables);
        startRequest.setAttachments(attachments);

        ProcessInstanceDTO instance = processRuntimeService.startAndSubmit(startRequest);
        return toResponse(instance);
    }

    private void validateFields(EntryApplicationSubmitRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        if (request.getApplicantName() == null || request.getApplicantName().trim().isEmpty()) {
            throw new IllegalArgumentException("请输入申请人姓名");
        }
        BigDecimal amount = request.getAmount();
        if (amount == null) {
            throw new IllegalArgumentException("请输入入金金额");
        }
        // BR001：入金金额必须大于 0（最小 0.01）。
        if (amount.compareTo(MIN_AMOUNT) < 0) {
            throw new IllegalArgumentException("入金金额必须大于0");
        }
    }

    /**
     * 校验 apply 节点付款凭证的数量、扩展名与大小约束，并转换为平台附件。
     * 非 apply 节点的附件一律不在此处生成。
     */
    private List<AttachmentUploadItem> buildBankReceiptAttachments(MultipartFile[] files) throws IOException {
        List<MultipartFile> normalized = new ArrayList<MultipartFile>();
        if (files != null) {
            for (MultipartFile file : files) {
                if (file != null && !file.isEmpty()) {
                    normalized.add(file);
                }
            }
        }
        if (normalized.size() < BANK_RECEIPT_MIN_COUNT) {
            throw new IllegalArgumentException("付款凭证至少需要上传1个文件");
        }
        if (normalized.size() > BANK_RECEIPT_MAX_COUNT) {
            throw new IllegalArgumentException("付款凭证最多上传5个文件");
        }
        List<AttachmentUploadItem> items = new ArrayList<AttachmentUploadItem>();
        for (MultipartFile file : normalized) {
            String fileName = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
            if (!BANK_RECEIPT_ALLOWED_EXTENSIONS.contains(extensionOf(fileName))) {
                throw new IllegalArgumentException("付款凭证仅支持 pdf、jpg、png 格式");
            }
            if (file.getSize() > BANK_RECEIPT_MAX_SIZE_BYTES) {
                throw new IllegalArgumentException("付款凭证单个文件不能超过10MB");
            }
            AttachmentUploadItem item = new AttachmentUploadItem();
            item.setAttachmentCode(ATTACHMENT_CODE_BANK_RECEIPT);
            item.setOwnerType(AttachmentOwnerTypeEnum.INSTANCE);
            item.setFileName(fileName);
            item.setContentType(file.getContentType());
            item.setSizeBytes(file.getSize());
            item.setContent(file.getBytes());
            items.add(item);
        }
        return items;
    }

    private String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /**
     * 由精确流程编码 + 受信用户 ID + 幂等键派生稳定 operationId。
     */
    String buildOperationId(String userId, String idempotencyKey) {
        return PROCESS_CODE + ":" + userId + ":" + idempotencyKey.trim();
    }

    /**
     * 将平台实例结果映射为响应：逐一映射 {@code ProcessInstanceDTO.getCreatedTasks()}
     * 中的每个任务，nodeName 映射为响应的 taskName，不产生空任务对象。
     * <p>
     * 平台实例状态返回 {@code InstanceStatusEnum}，响应按字符串形式暴露其标识
     * （enum 的 name/toString）。
     */
    private EntryApplicationSubmitResponse toResponse(ProcessInstanceDTO instance) {
        EntryApplicationSubmitResponse response = new EntryApplicationSubmitResponse();
        response.setInstanceId(instance.getInstanceId());
        response.setInstanceStatus(String.valueOf(instance.getInstanceStatus()));
        List<EntryApplicationSubmitResponse.TaskSummary> tasks =
                new ArrayList<EntryApplicationSubmitResponse.TaskSummary>();
        for (TaskDTO task : instance.getCreatedTasks()) {
            EntryApplicationSubmitResponse.TaskSummary summary = new EntryApplicationSubmitResponse.TaskSummary();
            summary.setTaskId(task.getTaskId());
            summary.setNodeCode(task.getNodeCode());
            summary.setTaskName(task.getNodeName());
            tasks.add(summary);
        }
        response.setTasks(tasks);
        return response;
    }
}
