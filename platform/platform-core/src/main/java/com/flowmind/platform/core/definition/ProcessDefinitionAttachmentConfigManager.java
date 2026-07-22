package com.flowmind.platform.core.definition;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowmind.platform.api.dto.ProcessAttachmentConfigDTO;
import com.flowmind.platform.api.dto.ProcessAttachmentTemplateDTO;
import com.flowmind.platform.api.dto.ProcessNodeDTO;
import com.flowmind.platform.api.dto.ValidationResult;
import com.flowmind.platform.api.enums.AttachmentConfigStatusEnum;
import com.flowmind.platform.api.enums.AttachmentTemplateStatusEnum;
import com.flowmind.platform.core.validation.FrozenValidationErrorCodes;
import com.flowmind.platform.core.validation.ProcessDefinitionAttachmentConfigValidator;
import com.flowmind.platform.core.validation.FrozenValidationErrorCodes;
import com.flowmind.platform.persistence.entity.ProcessAttachmentTemplateEntity;
import com.flowmind.platform.persistence.entity.ProcessDefinitionAttachmentConfigEntity;
import com.flowmind.platform.persistence.repository.ProcessAttachmentTemplateRepository;
import com.flowmind.platform.persistence.repository.ProcessDefinitionAttachmentConfigRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 流程定义附件配置组管理组件。
 *
 * <p>只管理定义期附件要求，不保存运行期附件文件或附件元数据。</p>
 *
 * @author FlowMind
 * @since 2026-07-17
 */
@Component
public class ProcessDefinitionAttachmentConfigManager {

    private final ProcessDefinitionAttachmentConfigRepository attachmentConfigRepository;
    private final ProcessAttachmentTemplateRepository attachmentTemplateRepository;
    private final ProcessDefinitionAttachmentConfigValidator attachmentConfigValidator;
    private final ObjectMapper objectMapper;

    /**
     * 创建流程定义附件配置组管理组件。
     *
     * @param attachmentConfigRepository 流程定义附件配置仓储
     * @param attachmentTemplateRepository 附件模板仓储
     * @param attachmentConfigValidator 附件配置校验器
     */
    public ProcessDefinitionAttachmentConfigManager(
            ProcessDefinitionAttachmentConfigRepository attachmentConfigRepository,
            ProcessAttachmentTemplateRepository attachmentTemplateRepository,
            ProcessDefinitionAttachmentConfigValidator attachmentConfigValidator) {
        this(attachmentConfigRepository, attachmentTemplateRepository, attachmentConfigValidator, new ObjectMapper());
    }

    /**
     * 创建流程定义附件配置组管理组件。
     *
     * @param attachmentConfigRepository 流程定义附件配置仓储
     * @param attachmentTemplateRepository 附件模板仓储
     * @param attachmentConfigValidator 附件配置校验器
     * @param objectMapper JSON 处理器
     */
    public ProcessDefinitionAttachmentConfigManager(
            ProcessDefinitionAttachmentConfigRepository attachmentConfigRepository,
            ProcessAttachmentTemplateRepository attachmentTemplateRepository,
            ProcessDefinitionAttachmentConfigValidator attachmentConfigValidator,
            ObjectMapper objectMapper) {
        this.attachmentConfigRepository = attachmentConfigRepository;
        this.attachmentTemplateRepository = attachmentTemplateRepository;
        this.attachmentConfigValidator = attachmentConfigValidator;
        this.objectMapper = objectMapper;
    }

    /**
     * 校验并保存草稿附件配置组。
     *
     * @param definitionId 流程定义 ID
     * @param attachmentConfigId 附件配置组 ID，为空时从配置项中取值或自动生成
     * @param configs 附件配置列表，空列表表示清空指定配置组的草稿附件要求
     * @param currentNodes 当前流程定义节点列表，用于校验适用节点
     * @param operatorUserId 操作人用户 ID
     * @return 附件配置校验结果
     */
    @Transactional
    public ValidationResult saveDraftGroup(String definitionId,
                                           String attachmentConfigId,
                                           List<ProcessAttachmentConfigDTO> configs,
                                           List<ProcessNodeDTO> currentNodes,
                                           String operatorUserId) {
        ValidationResult result = attachmentConfigValidator.validate(configs, currentNodes);
        if (!result.isValid()) {
            return result;
        }
        String groupId = resolveGroupId(attachmentConfigId, configs);
        if (configs == null || configs.isEmpty()) {
            attachmentConfigRepository.replaceDraftGroup(definitionId, groupId,
                    new ArrayList<ProcessDefinitionAttachmentConfigEntity>());
            return result;
        }
        attachmentConfigRepository.replaceDraftGroup(definitionId, groupId,
                toEntities(definitionId, groupId, configs, operatorUserId));
        return result;
    }

    /**
     * 查询流程定义下全部附件配置，并合并模板版本信息。
     *
     * @param definitionId 流程定义 ID
     * @return 附件配置与附件模板合并后的 DTO 列表
     */
    public List<ProcessAttachmentTemplateDTO> findByDefinitionId(String definitionId) {
        return toMergedDtos(attachmentConfigRepository.findByDefinitionId(definitionId));
    }

    /**
     * 查询流程定义下草稿附件配置，并合并模板版本信息。
     *
     * @param definitionId 流程定义 ID
     * @return 草稿附件配置与附件模板合并后的 DTO 列表
     */
    public List<ProcessAttachmentTemplateDTO> findDraftByDefinitionId(String definitionId) {
        return toMergedDtos(attachmentConfigRepository.findByDefinitionIdAndStatus(
                definitionId, AttachmentConfigStatusEnum.DRAFT.name()));
    }

    /**
     * 查询流程定义下当前生效附件配置组，并合并模板版本信息。
     *
     * @param definitionId 流程定义 ID
     * @return 生效附件配置与附件模板合并后的 DTO 列表
     */
    public List<ProcessAttachmentTemplateDTO> findActiveByDefinitionId(String definitionId) {
        return toMergedDtos(attachmentConfigRepository.findActiveByDefinitionId(definitionId));
    }

    /**
     * 激活指定附件配置组。
     *
     * @param definitionId 流程定义 ID
     * @param attachmentConfigId 待激活的附件配置组 ID
     * @param currentNodes 当前流程定义节点列表，用于激活前重新校验
     * @param operatorUserId 操作人用户 ID
     * @return 附件配置校验结果
     */
    @Transactional
    public ValidationResult activateGroup(String definitionId,
                                          String attachmentConfigId,
                                          List<ProcessNodeDTO> currentNodes,
                                          String operatorUserId) {
        List<ProcessDefinitionAttachmentConfigEntity> groupEntities =
                attachmentConfigRepository.findByDefinitionIdAndAttachmentConfigId(definitionId, attachmentConfigId);
        List<ProcessAttachmentConfigDTO> configs = toConfigDtos(groupEntities);
        ValidationResult result = attachmentConfigValidator.validate(configs, currentNodes);
        if (!result.isValid()) {
            return result;
        }
        if (!groupEntities.isEmpty()) {
            int activatedRows = attachmentConfigRepository.activateGroup(definitionId, attachmentConfigId,
                    operatorUserId);
            if (activatedRows != groupEntities.size()) {
                return invalidResult("attachment configuration group activation did not update every configured row");
            }
        }
        return result;
    }

    /**
     * 激活定义下唯一的草稿附件配置组。
     *
     * <p>流程图保存的正常路径最多只会留下一个草稿组。若发现多个草稿组，拒绝激活而不是
     * 任意选择其中一个，以保证运行时实例冻结到可追溯且确定的附件配置。</p>
     *
     * @param definitionId 流程定义 ID
     * @param currentNodes 当前流程定义节点列表
     * @param operatorUserId 操作人用户 ID
     * @return 附件配置校验结果；没有附件配置时返回通过结果
     */
    @Transactional
    public ValidationResult activateDraftGroup(String definitionId,
                                               List<ProcessNodeDTO> currentNodes,
                                               String operatorUserId) {
        List<ProcessDefinitionAttachmentConfigEntity> draftEntities =
                attachmentConfigRepository.findByDefinitionIdAndStatus(
                        definitionId, AttachmentConfigStatusEnum.DRAFT.name());
        if (draftEntities.isEmpty()) {
            return validResult();
        }
        Set<String> groupIds = new LinkedHashSet<String>();
        for (ProcessDefinitionAttachmentConfigEntity entity : draftEntities) {
            if (entity != null && !isBlank(entity.getAttachmentConfigId())) {
                groupIds.add(entity.getAttachmentConfigId());
            }
        }
        if (groupIds.size() != 1) {
            return invalidResult("definition must have exactly one draft attachment configuration group");
        }
        return activateGroup(definitionId, groupIds.iterator().next(), currentNodes, operatorUserId);
    }

    /**
     * 将源流程定义的附件配置复制到目标流程定义。
     *
     * @param sourceDefinitionId 源流程定义 ID
     * @param targetDefinitionId 目标流程定义 ID
     * @return 复制的附件配置行数
     */
    @Transactional
    public int copyAttachmentConfigs(String sourceDefinitionId, String targetDefinitionId) {
        return attachmentConfigRepository.copyToDefinition(sourceDefinitionId, targetDefinitionId);
    }

    /**
     * 删除流程定义下全部草稿附件配置。
     *
     * @param definitionId 流程定义 ID
     * @return 删除的草稿附件配置行数
     */
    public int deleteDraftAttachmentConfigs(String definitionId) {
        return attachmentConfigRepository.deleteDraftByDefinitionId(definitionId);
    }

    /**
     * 删除流程定义下全部附件配置。
     *
     * @param definitionId 流程定义 ID
     * @return 删除的附件配置行数
     */
    public int deleteAttachmentConfigs(String definitionId) {
        return attachmentConfigRepository.deleteByDefinitionId(definitionId);
    }

    /**
     * 只校验附件配置组，不写入数据库。
     *
     * @param configs 附件配置列表
     * @param currentNodes 当前流程定义节点列表
     * @return 附件配置校验结果
     */
    public ValidationResult validateAttachmentConfigs(List<ProcessAttachmentConfigDTO> configs,
                                                      List<ProcessNodeDTO> currentNodes) {
        return attachmentConfigValidator.validate(configs, currentNodes);
    }

    /**
     * 将附件配置 DTO 列表转换为草稿配置实体列表。
     *
     * @param definitionId 流程定义 ID
     * @param groupId 附件配置组 ID
     * @param configs 附件配置 DTO 列表
     * @param operatorUserId 操作人用户 ID
     * @return 附件配置实体列表
     */
    private List<ProcessDefinitionAttachmentConfigEntity> toEntities(String definitionId,
                                                                     String groupId,
                                                                     List<ProcessAttachmentConfigDTO> configs,
                                                                     String operatorUserId) {
        List<ProcessDefinitionAttachmentConfigEntity> result =
                new ArrayList<ProcessDefinitionAttachmentConfigEntity>();
        for (ProcessAttachmentConfigDTO config : configs) {
            ProcessDefinitionAttachmentConfigEntity entity = new ProcessDefinitionAttachmentConfigEntity();
            entity.setId(isBlank(config.getConfigId()) ? UUID.randomUUID().toString() : config.getConfigId().trim());
            entity.setAttachmentConfigId(groupId);
            entity.setDefinitionId(definitionId);
            entity.setConfigStatus(AttachmentConfigStatusEnum.DRAFT.name());
            entity.setAttachmentTemplateId(config.getAttachmentTemplateId().trim());
            entity.setAttachmentCode(config.getAttachmentCode().trim());
            entity.setRequired(config.getRequired());
            entity.setMinCount(config.getMinCount());
            entity.setMaxCount(config.getMaxCount());
            entity.setApplicableNodeCodes(toJson(safeStrings(config.getApplicableNodeCodes())));
            entity.setSortOrder(config.getSortOrder() == null ? 0 : config.getSortOrder());
            entity.setCreatedBy(operatorUserId);
            entity.setUpdatedBy(operatorUserId);
            result.add(entity);
        }
        return result;
    }

    /**
     * 将附件配置实体列表转换为合并模板信息后的 DTO 列表。
     *
     * @param configEntities 附件配置实体列表
     * @return 附件配置与模板合并后的 DTO 列表
     */
    private List<ProcessAttachmentTemplateDTO> toMergedDtos(
            List<ProcessDefinitionAttachmentConfigEntity> configEntities) {
        List<ProcessAttachmentTemplateDTO> result = new ArrayList<ProcessAttachmentTemplateDTO>();
        for (ProcessDefinitionAttachmentConfigEntity configEntity : configEntities) {
            ProcessAttachmentTemplateEntity templateEntity = findTemplate(configEntity.getAttachmentTemplateId());
            result.add(toMergedDto(configEntity, templateEntity));
        }
        return result;
    }

    /**
     * 将单条附件配置和对应模板版本合并为展示 DTO。
     *
     * @param configEntity 附件配置实体
     * @param templateEntity 附件模板实体
     * @return 附件配置与模板合并后的 DTO
     */
    private ProcessAttachmentTemplateDTO toMergedDto(ProcessDefinitionAttachmentConfigEntity configEntity,
                                                    ProcessAttachmentTemplateEntity templateEntity) {
        ProcessAttachmentTemplateDTO dto = new ProcessAttachmentTemplateDTO();
        dto.setId(configEntity.getId());
        dto.setAttachmentConfigId(configEntity.getAttachmentConfigId());
        dto.setDefinitionId(configEntity.getDefinitionId());
        dto.setConfigStatus(AttachmentConfigStatusEnum.valueOf(configEntity.getConfigStatus()));
        dto.setActivatedAt(configEntity.getActivatedAt());
        dto.setAttachmentTemplateId(configEntity.getAttachmentTemplateId());
        dto.setAttachmentCode(configEntity.getAttachmentCode());
        dto.setRequired(configEntity.getRequired());
        dto.setMinCount(configEntity.getMinCount());
        dto.setMaxCount(configEntity.getMaxCount());
        dto.setApplicableNodeCodes(fromJson(configEntity.getApplicableNodeCodes()));
        dto.setSortOrder(configEntity.getSortOrder());
        dto.setTemplateVersion(templateEntity.getTemplateVersion());
        dto.setAttachmentName(templateEntity.getAttachmentName());
        dto.setDescription(templateEntity.getDescription());
        dto.setAllowedExtensions(fromJson(templateEntity.getAllowedExtensions()));
        dto.setMaxSizeBytes(templateEntity.getMaxSizeBytes());
        dto.setTemplateStatus(AttachmentTemplateStatusEnum.valueOf(templateEntity.getTemplateStatus()));
        dto.setCreatedBy(configEntity.getCreatedBy());
        dto.setCreatedAt(configEntity.getCreatedAt());
        dto.setUpdatedBy(configEntity.getUpdatedBy());
        dto.setUpdatedAt(configEntity.getUpdatedAt());
        return dto;
    }

    /**
     * 将附件配置实体列表转换为配置 DTO 列表。
     *
     * @param configEntities 附件配置实体列表
     * @return 附件配置 DTO 列表
     */
    private List<ProcessAttachmentConfigDTO> toConfigDtos(
            List<ProcessDefinitionAttachmentConfigEntity> configEntities) {
        List<ProcessAttachmentConfigDTO> result = new ArrayList<ProcessAttachmentConfigDTO>();
        for (ProcessDefinitionAttachmentConfigEntity entity : configEntities) {
            ProcessAttachmentConfigDTO dto = new ProcessAttachmentConfigDTO();
            dto.setConfigId(entity.getId());
            dto.setAttachmentConfigId(entity.getAttachmentConfigId());
            dto.setDefinitionId(entity.getDefinitionId());
            dto.setAttachmentTemplateId(entity.getAttachmentTemplateId());
            dto.setAttachmentCode(entity.getAttachmentCode());
            dto.setRequired(entity.getRequired());
            dto.setMinCount(entity.getMinCount());
            dto.setMaxCount(entity.getMaxCount());
            dto.setApplicableNodeCodes(fromJson(entity.getApplicableNodeCodes()));
            dto.setSortOrder(entity.getSortOrder());
            result.add(dto);
        }
        return result;
    }

    /**
     * 查询必须存在的附件模板实体。
     *
     * @param templateId 附件模板版本 ID
     * @return 附件模板实体
     */
    private ProcessAttachmentTemplateEntity findTemplate(String templateId) {
        Optional<ProcessAttachmentTemplateEntity> template = attachmentTemplateRepository.findById(templateId);
        if (!template.isPresent()) {
            throw new IllegalStateException("Attachment template does not exist: " + templateId);
        }
        return template.get();
    }

    /**
     * 解析本次保存使用的附件配置组 ID。
     *
     * @param attachmentConfigId 方法入参中的附件配置组 ID
     * @param configs 附件配置列表
     * @return 附件配置组 ID
     */
    private String resolveGroupId(String attachmentConfigId, List<ProcessAttachmentConfigDTO> configs) {
        if (!isBlank(attachmentConfigId)) {
            return attachmentConfigId.trim();
        }
        for (ProcessAttachmentConfigDTO config : configs) {
            if (config != null && !isBlank(config.getAttachmentConfigId())) {
                return config.getAttachmentConfigId().trim();
            }
        }
        return UUID.randomUUID().toString();
    }

    private ValidationResult validResult() {
        ValidationResult result = new ValidationResult();
        result.setValid(true);
        return result;
    }

    private ValidationResult invalidResult(String message) {
        ValidationResult result = validResult();
        ValidationResult.Issue issue = new ValidationResult.Issue();
        issue.setCode(FrozenValidationErrorCodes.MODEL_ATTACHMENT_CONFIGURATION_INVALID);
        issue.setMessage(message);
        result.getIssues().add(issue);
        result.setValid(false);
        return result;
    }

    /**
     * 将字符串列表序列化为 JSON 数组。
     *
     * @param values 字符串列表
     * @return JSON 数组字符串
     */
    private String toJson(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize applicable node codes.", ex);
        }
    }

    /**
     * 将 JSON 数组字符串解析为字符串列表。
     *
     * @param json JSON 数组字符串
     * @return 字符串列表
     */
    private List<String> fromJson(String json) {
        try {
            List<String> result = new ArrayList<String>();
            if (isBlank(json)) {
                return result;
            }
            String[] values = objectMapper.readValue(json, String[].class);
            for (String value : values) {
                result.add(value);
            }
            return result;
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to parse applicable node codes.", ex);
        }
    }

    /**
     * 清理字符串列表中的空白值。
     *
     * @param values 原始字符串列表
     * @return 过滤空白并去首尾空格后的字符串列表
     */
    private List<String> safeStrings(List<String> values) {
        List<String> result = new ArrayList<String>();
        if (values == null) {
            return result;
        }
        for (String value : values) {
            if (!isBlank(value)) {
                result.add(value.trim());
            }
        }
        return result;
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

    /**
     * 创建单问题校验失败结果。
     *
     * @param code 错误码
     * @param message 错误信息
     * @return 校验失败结果
     */
    private ValidationResult invalidResult(String code, String message) {
        ValidationResult result = new ValidationResult();
        result.setValid(false);
        ValidationResult.Issue issue = new ValidationResult.Issue();
        issue.setCode(code);
        issue.setMessage(message);
        result.getIssues().add(issue);
        return result;
    }

    /**
     * 校验传入配置列表中的配置组 ID 是否与目标组一致。
     *
     * @param configs 配置列表
     * @param groupId 目标配置组 ID
     * @return 校验结果
     */
    private ValidationResult validateGroupIdConsistency(List<ProcessAttachmentConfigDTO> configs, String groupId) {
        ValidationResult result = new ValidationResult();
        result.setValid(true);
        for (int index = 0; index < configs.size(); index++) {
            ProcessAttachmentConfigDTO config = configs.get(index);
            if (config == null || isBlank(config.getAttachmentConfigId())) {
                continue;
            }
            String configGroupId = config.getAttachmentConfigId().trim();
            if (!groupId.equals(configGroupId)) {
                ValidationResult.Issue issue = new ValidationResult.Issue();
                issue.setCode(FrozenValidationErrorCodes.ATTACHMENT_CONFIG_REQUIRED);
                issue.setMessage("Attachment config group id at index " + index
                        + " does not match target group id: " + configGroupId + " vs " + groupId + ".");
                result.getIssues().add(issue);
                result.setValid(false);
            }
        }
        return result;
    }
}
