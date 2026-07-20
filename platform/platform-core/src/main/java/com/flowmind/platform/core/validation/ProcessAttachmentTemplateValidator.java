package com.flowmind.platform.core.validation;

import com.flowmind.platform.api.dto.ProcessAttachmentTemplateDTO;
import com.flowmind.platform.api.dto.ValidationResult;
import com.flowmind.platform.api.enums.AttachmentTemplateStatusEnum;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 附件模板版本定义校验器。
 *
 * <p>只校验定义期附件模板规则，不处理文件上传、下载或文件存储。</p>
 *
 * @author FlowMind
 * @since 2026-07-17
 */
@Component
public class ProcessAttachmentTemplateValidator {

    private static final Pattern CODE_PATTERN = Pattern.compile("[A-Za-z][A-Za-z0-9_]{0,63}");
    private static final Pattern EXTENSION_PATTERN = Pattern.compile("[a-z0-9][a-z0-9_-]{0,15}");

    /**
     * 校验新建附件模板版本请求。
     *
     * @param template 附件模板 DTO
     * @return 附件模板校验结果
     */
    public ValidationResult validateForCreate(ProcessAttachmentTemplateDTO template) {
        ValidationResult result = newValidResult();
        validateBaseFields(result, template, false);
        return result;
    }

    /**
     * 校验原地更新附件模板版本请求。
     *
     * @param template 附件模板 DTO
     * @return 附件模板校验结果
     */
    public ValidationResult validateForUpdate(ProcessAttachmentTemplateDTO template) {
        ValidationResult result = newValidResult();
        if (template == null || isBlank(template.getAttachmentTemplateId())) {
            addIssue(result, FrozenValidationErrorCodes.ATTACHMENT_TEMPLATE_REQUIRED,
                    "Attachment template id must not be blank.");
        }
        validateBaseFields(result, template, true);
        return result;
    }

    /**
     * 标准化允许上传的附件扩展名。
     *
     * @param allowedExtensions 原始扩展名列表
     * @return 去前导点、转小写、去重后的合法扩展名列表
     */
    public List<String> normalizeAllowedExtensions(List<String> allowedExtensions) {
        Set<String> normalized = new LinkedHashSet<String>();
        if (allowedExtensions == null) {
            return new ArrayList<String>();
        }
        for (String extension : allowedExtensions) {
            String value = normalizeExtension(extension);
            if (!isBlank(value) && EXTENSION_PATTERN.matcher(value).matches()) {
                normalized.add(value);
            }
        }
        return new ArrayList<String>(normalized);
    }

    /**
     * 校验附件模板基础字段。
     *
     * @param result 校验结果，会在发现问题时追加 issue
     * @param template 附件模板 DTO
     * @param requireStatus 是否要求模板状态必填
     */
    private void validateBaseFields(ValidationResult result,
                                    ProcessAttachmentTemplateDTO template,
                                    boolean requireStatus) {
        if (template == null) {
            addIssue(result, FrozenValidationErrorCodes.ATTACHMENT_TEMPLATE_REQUIRED,
                    "Attachment template must not be null.");
            return;
        }
        if (isBlank(template.getAttachmentCode()) || isBlank(template.getAttachmentName())) {
            addIssue(result, FrozenValidationErrorCodes.ATTACHMENT_TEMPLATE_REQUIRED,
                    "Attachment code and name must not be blank.");
        }
        if (!isBlank(template.getAttachmentCode())
                && !CODE_PATTERN.matcher(template.getAttachmentCode().trim()).matches()) {
            addIssue(result, FrozenValidationErrorCodes.ATTACHMENT_TEMPLATE_REQUIRED,
                    "Attachment code must start with a letter and contain only letters, digits or underscore.");
        }
        validateExtensions(result, template.getAllowedExtensions());
        if (template.getMaxSizeBytes() == null || template.getMaxSizeBytes() <= 0) {
            addIssue(result, FrozenValidationErrorCodes.ATTACHMENT_TEMPLATE_SIZE_INVALID,
                    "Attachment template maxSizeBytes must be greater than 0.");
        }
        if (requireStatus && template.getTemplateStatus() == null) {
            addIssue(result, FrozenValidationErrorCodes.ATTACHMENT_TEMPLATE_STATUS_INVALID,
                    "Attachment template status must not be null.");
        }
        if (template.getTemplateStatus() != null
                && !AttachmentTemplateStatusEnum.ENABLED.equals(template.getTemplateStatus())
                && !AttachmentTemplateStatusEnum.DISABLED.equals(template.getTemplateStatus())) {
            addIssue(result, FrozenValidationErrorCodes.ATTACHMENT_TEMPLATE_STATUS_INVALID,
                    "Attachment template status is invalid.");
        }
    }

    /**
     * 校验附件模板允许的扩展名列表。
     *
     * @param result 校验结果，会在发现问题时追加 issue
     * @param allowedExtensions 允许的扩展名列表
     */
    private void validateExtensions(ValidationResult result, List<String> allowedExtensions) {
        if (allowedExtensions == null || allowedExtensions.isEmpty()) {
            addIssue(result, FrozenValidationErrorCodes.ATTACHMENT_TEMPLATE_REQUIRED,
                    "Attachment template allowedExtensions must not be empty.");
            return;
        }
        Set<String> normalized = new LinkedHashSet<String>();
        for (String extension : allowedExtensions) {
            String value = normalizeExtension(extension);
            if (isBlank(value) || !EXTENSION_PATTERN.matcher(value).matches()) {
                addIssue(result, FrozenValidationErrorCodes.ATTACHMENT_TEMPLATE_EXTENSION_INVALID,
                        "Attachment extension is invalid: " + extension + ".");
                continue;
            }
            normalized.add(value);
        }
        if (normalized.isEmpty()) {
            addIssue(result, FrozenValidationErrorCodes.ATTACHMENT_TEMPLATE_EXTENSION_INVALID,
                    "Attachment template must contain at least one valid extension.");
        }
    }

    /**
     * 标准化单个附件扩展名。
     *
     * @param extension 原始扩展名
     * @return 去首尾空格、转小写并移除前导点后的扩展名
     */
    private String normalizeExtension(String extension) {
        if (extension == null) {
            return null;
        }
        String value = extension.trim().toLowerCase(Locale.ROOT);
        while (value.startsWith(".")) {
            value = value.substring(1);
        }
        return value;
    }

    /**
     * 创建默认通过的校验结果。
     *
     * @return 默认 valid 为 true 的校验结果
     */
    private ValidationResult newValidResult() {
        ValidationResult result = new ValidationResult();
        result.setValid(true);
        return result;
    }

    /**
     * 向校验结果追加错误。
     *
     * @param result 校验结果
     * @param code 错误码
     * @param message 错误信息
     */
    private void addIssue(ValidationResult result, String code, String message) {
        ValidationResult.Issue issue = new ValidationResult.Issue();
        issue.setCode(code);
        issue.setMessage(message);
        result.getIssues().add(issue);
        result.setValid(false);
    }

    /**
     * 判断字符串是否为空白。
     *
     * @param value 待判断字符串
     * @return 字符串为 null 或去空格后为空时返回 true
     */
    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
