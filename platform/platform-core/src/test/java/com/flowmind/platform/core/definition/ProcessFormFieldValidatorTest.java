package com.flowmind.platform.core.definition;

import com.flowmind.platform.api.dto.ProcessFormFieldDTO;
import com.flowmind.platform.api.dto.ValidationResult;
import com.flowmind.platform.core.validation.FrozenValidationErrorCodes;
import com.flowmind.platform.core.validation.ProcessFormFieldValidator;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProcessFormFieldValidatorTest {

    private final ProcessFormFieldValidator validator = new ProcessFormFieldValidator();

    @Test
    void validFormFieldsPass() {
        ValidationResult result = validator.validate(Arrays.asList(
                field("applicantName", "string", "input", "{\"maxLength\":64}", null),
                field("amount", "number", "number", "{\"min\":0}", "12.50"),
                field("applyDate", "date", "datePicker", null, "2026-07-17"),
                field("confirmed", "boolean", "checkbox", null, "true"),
                field("category", "select", "select", "{\"options\":[\"A\"]}", "A")));

        assertTrue(result.isValid());
        assertTrue(result.getIssues().isEmpty());
    }

    @Test
    void invalidFormFieldsReturnAllDetectedIssues() {
        ProcessFormFieldDTO duplicate = field("amount", "number", "number", null, null);
        ProcessFormFieldDTO duplicateAgain = field("amount", "number", "number", null, null);
        ProcessFormFieldDTO badType = field("badType", "object", "input", null, null);
        ProcessFormFieldDTO badControl = field("badControl", "date", "input", null, null);
        ProcessFormFieldDTO badJson = field("badJson", "string", "input", "{bad", null);
        ProcessFormFieldDTO badDefault = field("badDefault", "number", "number", null, "not-number");
        badDefault.setSortOrder(-1);

        ValidationResult result = validator.validate(Arrays.asList(
                duplicate, duplicateAgain, badType, badControl, badJson, badDefault));

        assertFalse(result.isValid());
        assertContainsCode(result, FrozenValidationErrorCodes.FORM_FIELD_CODE_DUPLICATED);
        assertContainsCode(result, FrozenValidationErrorCodes.FORM_FIELD_TYPE_INVALID);
        assertContainsCode(result, FrozenValidationErrorCodes.FORM_FIELD_CONTROL_TYPE_INVALID);
        assertContainsCode(result, FrozenValidationErrorCodes.FORM_FIELD_VALIDATION_RULE_INVALID);
        assertContainsCode(result, FrozenValidationErrorCodes.FORM_FIELD_DEFAULT_VALUE_INVALID);
        assertContainsCode(result, FrozenValidationErrorCodes.FORM_FIELD_SORT_ORDER_INVALID);
    }

    @Test
    void requiredFieldsMustBePresent() {
        ProcessFormFieldDTO field = new ProcessFormFieldDTO();
        field.setFieldCode("1bad");
        field.setRequired(null);

        ValidationResult result = validator.validate(Arrays.asList(field));

        assertFalse(result.isValid());
        assertContainsCode(result, FrozenValidationErrorCodes.FORM_FIELD_REQUIRED);
    }

    private ProcessFormFieldDTO field(String fieldCode, String fieldType, String controlType,
                                      String validationRule, String defaultValue) {
        ProcessFormFieldDTO field = new ProcessFormFieldDTO();
        field.setFieldCode(fieldCode);
        field.setFieldName(fieldCode + " name");
        field.setFieldType(fieldType);
        field.setControlType(controlType);
        field.setRequired(Boolean.TRUE);
        field.setValidationRule(validationRule);
        field.setDefaultValue(defaultValue);
        field.setSortOrder(1);
        return field;
    }

    private void assertContainsCode(ValidationResult result, String expectedCode) {
        for (ValidationResult.Issue issue : result.getIssues()) {
            if (expectedCode.equals(issue.getCode())) {
                return;
            }
        }
        throw new AssertionError("Expected issue code " + expectedCode + " but got " + result.getIssues());
    }
}
