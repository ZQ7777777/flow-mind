package com.flowmind.platform.api.dto;

import java.util.List;

/**
 * 附件模板校验结果。
 */
public class AttachmentTemplateCheckResult {

    /** 是否校验通过。 */
    private boolean passed;
    /** 缺失的附件编码列表。 */
    private List<String> missingAttachmentCodes;
    /** 校验错误信息列表。 */
    private List<String> errors;

    public AttachmentTemplateCheckResult() {
    }

    public boolean isPassed() {
        return passed;
    }

    public void setPassed(boolean passed) {
        this.passed = passed;
    }

    public List<String> getMissingAttachmentCodes() {
        return missingAttachmentCodes;
    }

    public void setMissingAttachmentCodes(List<String> missingAttachmentCodes) {
        this.missingAttachmentCodes = missingAttachmentCodes;
    }

    public List<String> getErrors() {
        return errors;
    }

    public void setErrors(List<String> errors) {
        this.errors = errors;
    }
}
