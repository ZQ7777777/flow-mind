package com.flowmind.platform.api.dto;

import java.util.List;

/**
 * 流程定义校验结果。
 */
public class ValidationResult {

    /** 是否校验通过。 */
    private boolean valid;
    /** 校验错误信息列表。 */
    private List<String> errors;
    /** 校验警告信息列表。 */
    private List<String> warnings;

    public ValidationResult() {
    }

    public boolean isValid() {
        return valid;
    }

    public void setValid(boolean valid) {
        this.valid = valid;
    }

    public List<String> getErrors() {
        return errors;
    }

    public void setErrors(List<String> errors) {
        this.errors = errors;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public void setWarnings(List<String> warnings) {
        this.warnings = warnings;
    }
}
