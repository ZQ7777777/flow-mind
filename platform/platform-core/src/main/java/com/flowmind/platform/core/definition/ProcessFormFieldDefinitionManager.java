package com.flowmind.platform.core.definition;

import com.flowmind.platform.api.dto.ProcessFormFieldDTO;
import com.flowmind.platform.api.dto.ValidationResult;
import com.flowmind.platform.core.validation.ProcessFormFieldValidator;
import com.flowmind.platform.persistence.entity.ProcessFormFieldEntity;
import com.flowmind.platform.persistence.repository.ProcessFormFieldRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 流程定义表单字段管理组件。
 *
 * <p>只管理定义期表单字段，不保存用户实际填写的表单值。</p>
 *
 * @author FlowMind
 * @since 2026-07-17
 */
@Component
public class ProcessFormFieldDefinitionManager {

    private final ProcessFormFieldRepository formFieldRepository;
    private final ProcessFormFieldValidator formFieldValidator;

    /**
     * 创建表单字段定义管理组件。
     *
     * @param formFieldRepository 表单字段仓储
     * @param formFieldValidator 表单字段校验器
     */
    public ProcessFormFieldDefinitionManager(ProcessFormFieldRepository formFieldRepository,
                                             ProcessFormFieldValidator formFieldValidator) {
        this.formFieldRepository = formFieldRepository;
        this.formFieldValidator = formFieldValidator;
    }

    /**
     * 校验并按流程定义整体替换保存表单字段。
     *
     * @param definitionId 流程定义 ID
     * @param formFields 表单字段列表，空列表表示清空当前定义字段
     * @return 表单字段校验结果
     */
    @Transactional
    public ValidationResult saveFormFields(String definitionId, List<ProcessFormFieldDTO> formFields) {
        ValidationResult result = formFieldValidator.validate(formFields);
        if (!result.isValid()) {
            return result;
        }
        formFieldRepository.replaceByDefinitionId(definitionId, toEntities(definitionId, formFields));
        return result;
    }

    /**
     * 查询流程定义下的表单字段。
     *
     * @param definitionId 流程定义 ID
     * @return 表单字段列表，按排序值和字段编码稳定排序
     */
    public List<ProcessFormFieldDTO> findFormFields(String definitionId) {
        List<ProcessFormFieldEntity> entities = formFieldRepository.findByDefinitionId(definitionId);
        List<ProcessFormFieldDTO> result = new ArrayList<ProcessFormFieldDTO>();
        for (ProcessFormFieldEntity entity : entities) {
            result.add(toDto(entity));
        }
        return result;
    }

    /**
     * 将源流程定义的表单字段复制到目标流程定义。
     *
     * @param sourceDefinitionId 源流程定义 ID
     * @param targetDefinitionId 目标流程定义 ID
     * @return 复制的表单字段数量
     */
    @Transactional
    public int copyFormFields(String sourceDefinitionId, String targetDefinitionId) {
        return formFieldRepository.copyToDefinition(sourceDefinitionId, targetDefinitionId);
    }

    /**
     * 删除流程定义下的全部表单字段。
     *
     * @param definitionId 流程定义 ID
     * @return 删除的表单字段数量
     */
    public int deleteFormFields(String definitionId) {
        return formFieldRepository.deleteByDefinitionId(definitionId);
    }

    /**
     * 只校验表单字段列表，不写入数据库。
     *
     * @param formFields 表单字段列表
     * @return 表单字段校验结果
     */
    public ValidationResult validateFormFields(List<ProcessFormFieldDTO> formFields) {
        return formFieldValidator.validate(formFields);
    }

    /**
     * 将表单字段 DTO 列表转换为数据库实体列表。
     *
     * @param definitionId 流程定义 ID
     * @param formFields 表单字段 DTO 列表
     * @return 表单字段实体列表
     */
    private List<ProcessFormFieldEntity> toEntities(String definitionId, List<ProcessFormFieldDTO> formFields) {
        List<ProcessFormFieldEntity> result = new ArrayList<ProcessFormFieldEntity>();
        if (formFields == null) {
            return result;
        }
        for (ProcessFormFieldDTO formField : formFields) {
            result.add(toEntity(definitionId, formField));
        }
        return result;
    }

    /**
     * 将单个表单字段 DTO 转换为数据库实体。
     *
     * @param definitionId 流程定义 ID
     * @param dto 表单字段 DTO
     * @return 表单字段实体
     */
    private ProcessFormFieldEntity toEntity(String definitionId, ProcessFormFieldDTO dto) {
        ProcessFormFieldEntity entity = new ProcessFormFieldEntity();
        entity.setId(isBlank(dto.getId()) ? UUID.randomUUID().toString() : dto.getId().trim());
        entity.setDefinitionId(definitionId);
        entity.setFieldCode(dto.getFieldCode().trim());
        entity.setFieldName(dto.getFieldName().trim());
        entity.setFieldType(dto.getFieldType().trim().toLowerCase(Locale.ROOT));
        entity.setControlType(dto.getControlType().trim());
        entity.setRequired(dto.getRequired());
        entity.setValidationRule(isBlank(dto.getValidationRule()) ? null : dto.getValidationRule().trim());
        entity.setDefaultValue(dto.getDefaultValue());
        entity.setSortOrder(dto.getSortOrder() == null ? 0 : dto.getSortOrder());
        return entity;
    }

    /**
     * 将表单字段实体转换为 DTO。
     *
     * @param entity 表单字段实体
     * @return 表单字段 DTO
     */
    private ProcessFormFieldDTO toDto(ProcessFormFieldEntity entity) {
        ProcessFormFieldDTO dto = new ProcessFormFieldDTO();
        dto.setId(entity.getId());
        dto.setDefinitionId(entity.getDefinitionId());
        dto.setFieldCode(entity.getFieldCode());
        dto.setFieldName(entity.getFieldName());
        dto.setFieldType(entity.getFieldType());
        dto.setControlType(entity.getControlType());
        dto.setRequired(entity.getRequired());
        dto.setValidationRule(entity.getValidationRule());
        dto.setDefaultValue(entity.getDefaultValue());
        dto.setSortOrder(entity.getSortOrder());
        return dto;
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
