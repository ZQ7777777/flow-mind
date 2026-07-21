package com.flowmind.platform.core.validation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowmind.platform.api.dto.ProcessFormFieldDTO;
import com.flowmind.platform.api.dto.ValidationResult;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 流程定义表单字段校验器。
 *
 * <p>只校验定义期字段结构，不校验或保存用户实际填写值。</p>
 *
 * @author FlowMind
 * @since 2026-07-17
 */
@Component
public class ProcessFormFieldValidator {

    private static final Pattern FIELD_CODE_PATTERN = Pattern.compile("[A-Za-z][A-Za-z0-9_]{0,63}");
    private static final Set<String> FIELD_TYPES = new LinkedHashSet<String>(
            Arrays.asList("string", "number", "date", "boolean", "select"));
    private static final Map<String, Set<String>> CONTROL_TYPES = buildControlTypes();

    private final ObjectMapper objectMapper;

    /**
     * 使用默认 JSON 解析器创建校验器。
     */
    public ProcessFormFieldValidator() {
        this(new ObjectMapper());
    }

    /**
     * 使用指定 JSON 解析器创建校验器。
     *
     * @param objectMapper JSON 解析器
     */
    public ProcessFormFieldValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 校验表单字段列表。
     *
     * @param formFields 表单字段列表，null 按空列表处理
     * @return 表单字段校验结果
     */
    public ValidationResult validate(List<ProcessFormFieldDTO> formFields) {
        ValidationResult result = new ValidationResult();
        result.setValid(true);
        List<ProcessFormFieldDTO> fields = formFields == null
                ? new ArrayList<ProcessFormFieldDTO>() : formFields;
        Set<String> fieldCodes = new LinkedHashSet<String>();
        for (int index = 0; index < fields.size(); index++) {
            ProcessFormFieldDTO field = fields.get(index);
            validateField(result, field, fieldCodes, index);
        }
        return result;
    }

    /**
     * 校验单个表单字段。
     *
     * @param result 校验结果，会在发现问题时追加 issue
     * @param field 表单字段 DTO
     * @param fieldCodes 当前批次已出现的字段编码集合
     * @param index 字段在列表中的下标
     */
    private void validateField(ValidationResult result,
                               ProcessFormFieldDTO field,
                               Set<String> fieldCodes,
                               int index) {
        if (field == null) {
            addIssue(result, FrozenValidationErrorCodes.FORM_FIELD_REQUIRED,
                    "Form field at index " + index + " must not be null.");
            return;
        }

        String fieldCode = trim(field.getFieldCode());
        String fieldName = trim(field.getFieldName());
        String fieldType = normalize(field.getFieldType());
        String controlType = trim(field.getControlType());

        if (isBlank(fieldCode) || isBlank(fieldName) || isBlank(fieldType) || isBlank(controlType)
                || field.getRequired() == null) {
            addIssue(result, FrozenValidationErrorCodes.FORM_FIELD_REQUIRED,
                    "Form field code, name, type, control type and required flag must not be blank.");
        }
        if (!isBlank(fieldCode) && !FIELD_CODE_PATTERN.matcher(fieldCode).matches()) {
            addIssue(result, FrozenValidationErrorCodes.FORM_FIELD_REQUIRED,
                    "Form field code must start with a letter and contain only letters, digits or underscore: "
                            + fieldCode + ".");
        }
        if (!isBlank(fieldCode) && !fieldCodes.add(fieldCode)) {
            addIssue(result, FrozenValidationErrorCodes.FORM_FIELD_CODE_DUPLICATED,
                    "Duplicate form field code: " + fieldCode + ".");
        }

        validateType(result, fieldType, controlType, fieldCode);
        validateSortOrder(result, field.getSortOrder(), fieldCode);
        validateJson(result, field.getValidationRule(), fieldCode);
        validateDefaultValue(result, field.getDefaultValue(), fieldType, fieldCode);
    }

    /**
     * 校验字段类型和控件类型是否受支持且互相兼容。
     *
     * @param result 校验结果，会在发现问题时追加 issue
     * @param fieldType 字段类型
     * @param controlType 控件类型
     * @param fieldCode 字段编码，用于错误信息
     */
    private void validateType(ValidationResult result, String fieldType, String controlType, String fieldCode) {
        controlType = normalize(controlType);
        if (isBlank(fieldType)) {
            return;
        }
        if (!FIELD_TYPES.contains(fieldType)) {
            addIssue(result, FrozenValidationErrorCodes.FORM_FIELD_TYPE_INVALID,
                    "Unsupported form field type: " + fieldType + ".");
            return;
        }
        Set<String> allowedControls = CONTROL_TYPES.get(fieldType);
        if (!isBlank(controlType) && (allowedControls == null || !allowedControls.contains(controlType))) {
            addIssue(result, FrozenValidationErrorCodes.FORM_FIELD_CONTROL_TYPE_INVALID,
                    "Control type " + controlType + " is not compatible with field "
                            + fieldCode + " type " + fieldType + ".");
        }
    }

    /**
     * 校验字段排序值。
     *
     * @param result 校验结果，会在发现问题时追加 issue
     * @param sortOrder 排序值
     * @param fieldCode 字段编码，用于错误信息
     */
    private void validateSortOrder(ValidationResult result, Integer sortOrder, String fieldCode) {
        if (sortOrder != null && sortOrder < 0) {
            addIssue(result, FrozenValidationErrorCodes.FORM_FIELD_SORT_ORDER_INVALID,
                    "Form field " + fieldCode + " sortOrder must not be negative.");
        }
    }

    /**
     * 校验字段校验规则是否为合法 JSON。
     *
     * @param result 校验结果，会在发现问题时追加 issue
     * @param validationRule 校验规则 JSON 字符串
     * @param fieldCode 字段编码，用于错误信息
     */
    private void validateJson(ValidationResult result, String validationRule, String fieldCode) {
        if (isBlank(validationRule)) {
            return;
        }
        try {
            objectMapper.readTree(validationRule);
        } catch (Exception ex) {
            addIssue(result, FrozenValidationErrorCodes.FORM_FIELD_VALIDATION_RULE_INVALID,
                    "Form field " + fieldCode + " validationRule must be valid JSON.");
        }
    }

    /**
     * 校验默认值是否与字段类型匹配。
     *
     * @param result 校验结果，会在发现问题时追加 issue
     * @param defaultValue 默认值
     * @param fieldType 字段类型
     * @param fieldCode 字段编码，用于错误信息
     */
    private void validateDefaultValue(ValidationResult result, String defaultValue, String fieldType, String fieldCode) {
        if (isBlank(defaultValue) || isBlank(fieldType) || !FIELD_TYPES.contains(fieldType)) {
            return;
        }
        try {
            if ("number".equals(fieldType)) {
                new BigDecimal(defaultValue);
            } else if ("boolean".equals(fieldType)) {
                String normalized = normalize(defaultValue);
                if (!"true".equals(normalized) && !"false".equals(normalized)) {
                    throw new IllegalArgumentException("Boolean default value must be true or false.");
                }
            } else if ("date".equals(fieldType)) {
                LocalDate.parse(defaultValue);
            }
        } catch (Exception ex) {
            addIssue(result, FrozenValidationErrorCodes.FORM_FIELD_DEFAULT_VALUE_INVALID,
                    "Form field " + fieldCode + " defaultValue is not compatible with type " + fieldType + ".");
        }
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

    /**
     * 对字符串做空安全去空格处理。
     *
     * @param value 待处理字符串
     * @return null 原样返回，否则返回去首尾空格后的字符串
     */
    private String trim(String value) {
        return value == null ? null : value.trim();
    }

    /**
     * 对字符串做空安全标准化处理。
     *
     * @param value 待处理字符串
     * @return null 原样返回，否则返回去首尾空格并转小写后的字符串
     */
    private String normalize(String value) {
        return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 构建字段类型与可用控件类型的映射。
     *
     * @return 字段类型到控件类型集合的映射
     */
    private static Map<String, Set<String>> buildControlTypes() {
        Map<String, Set<String>> result = new LinkedHashMap<String, Set<String>>();
        result.put("string", new LinkedHashSet<String>(Arrays.asList("input", "textarea", "select")));
        result.put("number", new LinkedHashSet<String>(Arrays.asList("number", "input")));
        result.put("date", new LinkedHashSet<String>(Arrays.asList("datepicker")));
        result.put("boolean", new LinkedHashSet<String>(Arrays.asList("checkbox", "select")));
        result.put("select", new LinkedHashSet<String>(Arrays.asList("select")));
        return result;
    }
}
