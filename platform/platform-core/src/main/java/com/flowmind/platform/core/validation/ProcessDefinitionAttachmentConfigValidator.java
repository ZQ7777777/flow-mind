package com.flowmind.platform.core.validation;

import com.flowmind.platform.api.dto.ProcessAttachmentConfigDTO;
import com.flowmind.platform.api.dto.ProcessNodeDTO;
import com.flowmind.platform.api.dto.ValidationResult;
import com.flowmind.platform.api.enums.AttachmentTemplateStatusEnum;
import com.flowmind.platform.api.enums.NodeTypeEnum;
import com.flowmind.platform.persistence.entity.ProcessAttachmentTemplateEntity;
import com.flowmind.platform.persistence.repository.ProcessAttachmentTemplateRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 流程定义附件配置组校验器。
 *
 * <p>只校验定义期附件要求，不检查运行期实际附件是否已经上传。</p>
 *
 * @author FlowMind
 * @since 2026-07-17
 */
@Component
public class ProcessDefinitionAttachmentConfigValidator {

    private final ProcessAttachmentTemplateRepository attachmentTemplateRepository;

    /**
     * 创建附件配置组校验器。
     *
     * @param attachmentTemplateRepository 附件模板仓储
     */
    public ProcessDefinitionAttachmentConfigValidator(
            ProcessAttachmentTemplateRepository attachmentTemplateRepository) {
        this.attachmentTemplateRepository = attachmentTemplateRepository;
    }

    /**
     * 校验附件配置组。
     *
     * @param configs 附件配置列表，null 按空列表处理
     * @param currentNodes 当前流程定义节点列表，用于校验适用节点
     * @return 附件配置校验结果
     */
    public ValidationResult validate(List<ProcessAttachmentConfigDTO> configs,
                                     List<ProcessNodeDTO> currentNodes) {
        ValidationResult result = newValidResult();
        List<ProcessAttachmentConfigDTO> safeConfigs = configs == null
                ? new ArrayList<ProcessAttachmentConfigDTO>() : configs;
        Map<String, ProcessNodeDTO> nodeByCode = indexNodes(currentNodes);
        Set<String> attachmentCodes = new LinkedHashSet<String>();
        Set<String> templateIds = new LinkedHashSet<String>();
        for (int index = 0; index < safeConfigs.size(); index++) {
            validateConfig(result, safeConfigs.get(index), nodeByCode, attachmentCodes, templateIds, index);
        }
        return result;
    }

    /**
     * 校验单条附件配置。
     *
     * @param result 校验结果，会在发现问题时追加 issue
     * @param config 附件配置 DTO
     * @param nodeByCode 当前节点编码到节点 DTO 的索引
     * @param attachmentCodes 当前配置组已出现的附件编码集合
     * @param templateIds 当前配置组已出现的模板版本 ID 集合
     * @param index 配置在列表中的下标
     */
    private void validateConfig(ValidationResult result,
                                ProcessAttachmentConfigDTO config,
                                Map<String, ProcessNodeDTO> nodeByCode,
                                Set<String> attachmentCodes,
                                Set<String> templateIds,
                                int index) {
        if (config == null) {
            addIssue(result, FrozenValidationErrorCodes.ATTACHMENT_CONFIG_REQUIRED,
                    "Attachment config at index " + index + " must not be null.");
            return;
        }
        if (isBlank(config.getAttachmentTemplateId()) || isBlank(config.getAttachmentCode())
                || config.getRequired() == null || config.getMinCount() == null) {
            addIssue(result, FrozenValidationErrorCodes.ATTACHMENT_CONFIG_REQUIRED,
                    "Attachment template id, code, required flag and minCount must not be blank.");
        }
        ProcessAttachmentTemplateEntity template = validateTemplate(result, config);
        validateDuplicates(result, config, attachmentCodes, templateIds);
        validateQuantity(result, config);
        validateSortOrder(result, config);
        validateNodes(result, config, nodeByCode);
        if (template != null && !isBlank(config.getAttachmentCode())
                && !template.getAttachmentCode().equals(config.getAttachmentCode())) {
            addIssue(result, FrozenValidationErrorCodes.ATTACHMENT_CONFIG_TEMPLATE_INVALID,
                    "Attachment code must match referenced template version.");
        }
    }

    /**
     * 校验附件配置引用的模板版本。
     *
     * @param result 校验结果，会在发现问题时追加 issue
     * @param config 附件配置 DTO
     * @return 引用的附件模板实体，不存在或模板 ID 为空时返回 null
     */
    private ProcessAttachmentTemplateEntity validateTemplate(ValidationResult result,
                                                            ProcessAttachmentConfigDTO config) {
        if (isBlank(config.getAttachmentTemplateId())) {
            return null;
        }
        Optional<ProcessAttachmentTemplateEntity> template =
                attachmentTemplateRepository.findById(config.getAttachmentTemplateId());
        if (!template.isPresent()) {
            addIssue(result, FrozenValidationErrorCodes.ATTACHMENT_CONFIG_TEMPLATE_INVALID,
                    "Attachment template version does not exist: " + config.getAttachmentTemplateId() + ".");
            return null;
        }
        if (!AttachmentTemplateStatusEnum.ENABLED.name().equals(template.get().getTemplateStatus())) {
            addIssue(result, FrozenValidationErrorCodes.ATTACHMENT_CONFIG_TEMPLATE_DISABLED,
                    "Attachment template version is disabled: " + config.getAttachmentTemplateId() + ".");
        }
        return template.get();
    }

    /**
     * 校验同一附件配置组内的附件编码和模板版本是否重复。
     *
     * @param result 校验结果，会在发现问题时追加 issue
     * @param config 附件配置 DTO
     * @param attachmentCodes 当前配置组已出现的附件编码集合
     * @param templateIds 当前配置组已出现的模板版本 ID 集合
     */
    private void validateDuplicates(ValidationResult result,
                                    ProcessAttachmentConfigDTO config,
                                    Set<String> attachmentCodes,
                                    Set<String> templateIds) {
        if (!isBlank(config.getAttachmentCode()) && !attachmentCodes.add(config.getAttachmentCode())) {
            addIssue(result, FrozenValidationErrorCodes.ATTACHMENT_CONFIG_DUPLICATED,
                    "Duplicate attachment code in config group: " + config.getAttachmentCode() + ".");
        }
        if (!isBlank(config.getAttachmentTemplateId()) && !templateIds.add(config.getAttachmentTemplateId())) {
            addIssue(result, FrozenValidationErrorCodes.ATTACHMENT_CONFIG_DUPLICATED,
                    "Duplicate attachment template in config group: " + config.getAttachmentTemplateId() + ".");
        }
    }

    /**
     * 校验附件数量限制。
     *
     * @param result 校验结果，会在发现问题时追加 issue
     * @param config 附件配置 DTO
     */
    private void validateQuantity(ValidationResult result, ProcessAttachmentConfigDTO config) {
        Integer minCount = config.getMinCount();
        Integer maxCount = config.getMaxCount();
        if (minCount != null && minCount < 0) {
            addIssue(result, FrozenValidationErrorCodes.ATTACHMENT_CONFIG_QUANTITY_INVALID,
                    "Attachment minCount must not be negative.");
        }
        if (maxCount != null && maxCount < 1) {
            addIssue(result, FrozenValidationErrorCodes.ATTACHMENT_CONFIG_QUANTITY_INVALID,
                    "Attachment maxCount must be greater than 0 when specified.");
        }
        if (minCount != null && maxCount != null && maxCount < minCount) {
            addIssue(result, FrozenValidationErrorCodes.ATTACHMENT_CONFIG_QUANTITY_INVALID,
                    "Attachment maxCount must be greater than or equal to minCount.");
        }
        if (Boolean.TRUE.equals(config.getRequired()) && minCount != null && minCount < 1) {
            addIssue(result, FrozenValidationErrorCodes.ATTACHMENT_CONFIG_QUANTITY_INVALID,
                    "Required attachment minCount must be at least 1.");
        }
    }

    /**
     * 校验附件配置排序值。
     *
     * @param result 校验结果，会在发现问题时追加 issue
     * @param config 附件配置 DTO
     */
    private void validateSortOrder(ValidationResult result, ProcessAttachmentConfigDTO config) {
        if (config.getSortOrder() != null && config.getSortOrder() < 0) {
            addIssue(result, FrozenValidationErrorCodes.ATTACHMENT_CONFIG_SORT_ORDER_INVALID,
                    "Attachment config sortOrder must not be negative.");
        }
    }

    /**
     * 校验附件配置适用节点。
     *
     * @param result 校验结果，会在发现问题时追加 issue
     * @param config 附件配置 DTO
     * @param nodeByCode 当前节点编码到节点 DTO 的索引
     */
    private void validateNodes(ValidationResult result,
                               ProcessAttachmentConfigDTO config,
                               Map<String, ProcessNodeDTO> nodeByCode) {
        List<String> nodeCodes = config.getApplicableNodeCodes();
        if (nodeCodes == null || nodeCodes.isEmpty()) {
            return;
        }
        Set<String> seenNodeCodes = new LinkedHashSet<String>();
        for (String nodeCode : nodeCodes) {
            if (isBlank(nodeCode) || !seenNodeCodes.add(nodeCode)) {
                addIssue(result, FrozenValidationErrorCodes.ATTACHMENT_CONFIG_NODE_INVALID,
                        "Applicable node code must not be blank or duplicated.");
                continue;
            }
            ProcessNodeDTO node = nodeByCode.get(nodeCode);
            if (node == null || !NodeTypeEnum.USER_TASK.equals(node.getNodeType())) {
                addIssue(result, FrozenValidationErrorCodes.ATTACHMENT_CONFIG_NODE_INVALID,
                        "Applicable node must exist and be USER_TASK: " + nodeCode + ".");
            }
        }
    }

    /**
     * 按节点编码索引当前流程节点。
     *
     * @param currentNodes 当前流程定义节点列表
     * @return 节点编码到节点 DTO 的映射
     */
    private Map<String, ProcessNodeDTO> indexNodes(List<ProcessNodeDTO> currentNodes) {
        Map<String, ProcessNodeDTO> result = new LinkedHashMap<String, ProcessNodeDTO>();
        if (currentNodes == null) {
            return result;
        }
        for (ProcessNodeDTO node : currentNodes) {
            if (node != null && !isBlank(node.getNodeCode())) {
                result.put(node.getNodeCode(), node);
            }
        }
        return result;
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
