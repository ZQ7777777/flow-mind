package com.flowmind.platform.core.definition;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowmind.platform.api.dto.ProcessAttachmentTemplateDTO;
import com.flowmind.platform.api.dto.ValidationResult;
import com.flowmind.platform.api.enums.AttachmentTemplateStatusEnum;
import com.flowmind.platform.core.validation.FrozenValidationErrorCodes;
import com.flowmind.platform.core.validation.FrozenValidationException;
import com.flowmind.platform.core.validation.ProcessAttachmentTemplateValidator;
import com.flowmind.platform.persistence.entity.ProcessAttachmentTemplateEntity;
import com.flowmind.platform.persistence.repository.ProcessAttachmentTemplateRepository;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 附件模板版本化管理组件。
 *
 * <p>只管理定义期附件模板版本，不处理运行期附件文件或附件元数据。</p>
 *
 * @author FlowMind
 * @since 2026-07-17
 */
@Component
public class ProcessAttachmentTemplateManager {

    private final ProcessAttachmentTemplateRepository attachmentTemplateRepository;
    private final ProcessAttachmentTemplateValidator attachmentTemplateValidator;
    private final ObjectMapper objectMapper;

    /**
     * 创建附件模板版本化管理组件。
     *
     * @param attachmentTemplateRepository 附件模板仓储
     * @param attachmentTemplateValidator 附件模板校验器
     */
    public ProcessAttachmentTemplateManager(ProcessAttachmentTemplateRepository attachmentTemplateRepository,
                                            ProcessAttachmentTemplateValidator attachmentTemplateValidator) {
        this(attachmentTemplateRepository, attachmentTemplateValidator, new ObjectMapper());
    }

    /**
     * 创建附件模板版本化管理组件。
     *
     * @param attachmentTemplateRepository 附件模板仓储
     * @param attachmentTemplateValidator 附件模板校验器
     * @param objectMapper JSON 处理器
     */
    public ProcessAttachmentTemplateManager(ProcessAttachmentTemplateRepository attachmentTemplateRepository,
                                            ProcessAttachmentTemplateValidator attachmentTemplateValidator,
                                            ObjectMapper objectMapper) {
        this.attachmentTemplateRepository = attachmentTemplateRepository;
        this.attachmentTemplateValidator = attachmentTemplateValidator;
        this.objectMapper = objectMapper;
    }

    /**
     * 创建同一附件编码下的下一个模板版本。
     *
     * @param request 附件模板创建请求
     * @param operatorUserId 操作人用户 ID
     * @return 新创建的附件模板版本
     */
    public ProcessAttachmentTemplateDTO createTemplateVersion(ProcessAttachmentTemplateDTO request,
                                                              String operatorUserId) {
        ValidationResult validationResult = attachmentTemplateValidator.validateForCreate(request);
        throwIfInvalid(validationResult);
        String attachmentCode = request.getAttachmentCode().trim();
        int nextVersion = attachmentTemplateRepository.findMaxVersionByAttachmentCode(attachmentCode) + 1;
        ProcessAttachmentTemplateEntity entity = new ProcessAttachmentTemplateEntity();
        entity.setId(UUID.randomUUID().toString());
        entity.setAttachmentCode(attachmentCode);
        entity.setTemplateVersion(nextVersion);
        entity.setAttachmentName(request.getAttachmentName().trim());
        entity.setDescription(trimToNull(request.getDescription()));
        entity.setAllowedExtensions(toJson(attachmentTemplateValidator.normalizeAllowedExtensions(
                request.getAllowedExtensions())));
        entity.setMaxSizeBytes(request.getMaxSizeBytes());
        entity.setTemplateStatus(AttachmentTemplateStatusEnum.ENABLED.name());
        entity.setCreatedBy(operatorUserId);
        entity.setUpdatedBy(operatorUserId);
        attachmentTemplateRepository.insert(entity);
        return findById(entity.getId()).get();
    }

    /**
     * 原地更新未被生效或历史配置引用的附件模板版本。
     *
     * @param request 附件模板更新请求
     * @param operatorUserId 操作人用户 ID
     * @return 更新后的附件模板版本
     */
    public ProcessAttachmentTemplateDTO updateTemplate(ProcessAttachmentTemplateDTO request,
                                                       String operatorUserId) {
        ValidationResult validationResult = attachmentTemplateValidator.validateForUpdate(request);
        throwIfInvalid(validationResult);
        ProcessAttachmentTemplateEntity existing = getExisting(request.getAttachmentTemplateId());
        List<String> normalizedExtensions = attachmentTemplateValidator.normalizeAllowedExtensions(
                request.getAllowedExtensions());
        String allowedExtensionsJson = toJson(normalizedExtensions);
        boolean destructiveChange = !existing.getAllowedExtensions().equals(allowedExtensionsJson)
                || !existing.getMaxSizeBytes().equals(request.getMaxSizeBytes());
        if (destructiveChange && attachmentTemplateRepository.isReferencedByEffectiveConfig(existing.getId())) {
            throw new FrozenValidationException(FrozenValidationErrorCodes.ATTACHMENT_TEMPLATE_REFERENCED,
                    "Referenced attachment template version cannot be modified in place.");
        }
        attachmentTemplateRepository.updateTemplate(existing.getId(), request.getAttachmentName().trim(),
                trimToNull(request.getDescription()), allowedExtensionsJson, request.getMaxSizeBytes(),
                request.getTemplateStatus().name(), operatorUserId);
        return findById(existing.getId()).get();
    }

    /**
     * 禁用附件模板版本。
     *
     * @param attachmentTemplateId 附件模板版本 ID
     * @param operatorUserId 操作人用户 ID
     * @return 禁用后的附件模板版本
     */
    public ProcessAttachmentTemplateDTO disableTemplate(String attachmentTemplateId, String operatorUserId) {
        ProcessAttachmentTemplateEntity existing = getExisting(attachmentTemplateId);
        attachmentTemplateRepository.updateStatus(existing.getId(), AttachmentTemplateStatusEnum.DISABLED.name(),
                operatorUserId);
        return findById(existing.getId()).get();
    }

    /**
     * 按附件模板版本 ID 查询模板。
     *
     * @param attachmentTemplateId 附件模板版本 ID
     * @return 附件模板版本，不存在时为空
     */
    public Optional<ProcessAttachmentTemplateDTO> findById(String attachmentTemplateId) {
        Optional<ProcessAttachmentTemplateEntity> entity = attachmentTemplateRepository.findById(attachmentTemplateId);
        return entity.isPresent() ? Optional.of(toDto(entity.get())) : Optional.empty();
    }

    /**
     * 按附件编码、状态和版本查询模板版本。
     *
     * @param attachmentCode 附件编码，为空时不按编码过滤
     * @param templateStatus 模板状态，为空时不按状态过滤
     * @param templateVersion 模板版本号，为空时不按版本过滤
     * @return 附件模板版本列表
     */
    public List<ProcessAttachmentTemplateDTO> findTemplates(String attachmentCode,
                                                            AttachmentTemplateStatusEnum templateStatus,
                                                            Integer templateVersion) {
        List<ProcessAttachmentTemplateEntity> entities = attachmentTemplateRepository.findTemplates(
                attachmentCode,
                templateStatus == null ? null : templateStatus.name(),
                templateVersion);
        List<ProcessAttachmentTemplateDTO> result = new ArrayList<ProcessAttachmentTemplateDTO>();
        for (ProcessAttachmentTemplateEntity entity : entities) {
            result.add(toDto(entity));
        }
        return result;
    }

    /**
     * 只校验新建附件模板版本请求。
     *
     * @param request 附件模板创建请求
     * @return 附件模板校验结果
     */
    public ValidationResult validateForCreate(ProcessAttachmentTemplateDTO request) {
        return attachmentTemplateValidator.validateForCreate(request);
    }

    /**
     * 查询存在的附件模板版本。
     *
     * @param attachmentTemplateId 附件模板版本 ID
     * @return 附件模板实体
     */
    private ProcessAttachmentTemplateEntity getExisting(String attachmentTemplateId) {
        Optional<ProcessAttachmentTemplateEntity> existing = attachmentTemplateRepository.findById(attachmentTemplateId);
        if (!existing.isPresent()) {
            throw new FrozenValidationException(FrozenValidationErrorCodes.ATTACHMENT_TEMPLATE_NOT_FOUND,
                    "Attachment template version does not exist: " + attachmentTemplateId + ".");
        }
        return existing.get();
    }

    /**
     * 将附件模板实体转换为 DTO。
     *
     * @param entity 附件模板实体
     * @return 附件模板 DTO
     */
    private ProcessAttachmentTemplateDTO toDto(ProcessAttachmentTemplateEntity entity) {
        ProcessAttachmentTemplateDTO dto = new ProcessAttachmentTemplateDTO();
        dto.setAttachmentTemplateId(entity.getId());
        dto.setAttachmentCode(entity.getAttachmentCode());
        dto.setTemplateVersion(entity.getTemplateVersion());
        dto.setAttachmentName(entity.getAttachmentName());
        dto.setDescription(entity.getDescription());
        dto.setAllowedExtensions(fromJson(entity.getAllowedExtensions()));
        dto.setMaxSizeBytes(entity.getMaxSizeBytes());
        dto.setTemplateStatus(AttachmentTemplateStatusEnum.valueOf(entity.getTemplateStatus()));
        dto.setCreatedBy(entity.getCreatedBy());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedBy(entity.getUpdatedBy());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }

    /**
     * 将允许的扩展名列表序列化为 JSON 数组字符串。
     *
     * @param allowedExtensions 允许的扩展名列表
     * @return JSON 数组字符串
     */
    private String toJson(List<String> allowedExtensions) {
        try {
            return objectMapper.writeValueAsString(allowedExtensions);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to serialize attachment extensions.", ex);
        }
    }

    /**
     * 将允许的扩展名 JSON 数组字符串反序列化为列表。
     *
     * @param allowedExtensions 允许的扩展名 JSON 数组字符串
     * @return 扩展名列表
     */
    private List<String> fromJson(String allowedExtensions) {
        try {
            String[] values = objectMapper.readValue(allowedExtensions, String[].class);
            List<String> result = new ArrayList<String>();
            for (String value : values) {
                result.add(value);
            }
            return result;
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to parse attachment extensions.", ex);
        }
    }

    /**
     * 校验失败时抛出冻结校验异常。
     *
     * @param validationResult 校验结果
     */
    private void throwIfInvalid(ValidationResult validationResult) {
        if (validationResult.isValid()) {
            return;
        }
        ValidationResult.Issue issue = validationResult.getIssues().isEmpty()
                ? null : validationResult.getIssues().get(0);
        String code = issue == null ? FrozenValidationErrorCodes.ATTACHMENT_TEMPLATE_REQUIRED : issue.getCode();
        String message = issue == null ? "Attachment template validation failed." : issue.getMessage();
        throw new FrozenValidationException(code, message);
    }

    /**
     * 将空白字符串转换为 null。
     *
     * @param value 待处理字符串
     * @return null 或去首尾空格后的字符串
     */
    private String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }
}
