package com.flowmind.business.workflow.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.PositiveOrZero;
import java.util.List;
import java.util.Map;

public final class WorkflowActionRequests {
    private WorkflowActionRequests() { }

    public static class Basic {
        @NotNull
        @PositiveOrZero
        private Long expectedTaskVersion;
        private String comment;
        public Long getExpectedTaskVersion() { return expectedTaskVersion; }
        public void setExpectedTaskVersion(Long expectedTaskVersion) { this.expectedTaskVersion = expectedTaskVersion; }
        public String getComment() { return comment; }
        public void setComment(String comment) { this.comment = comment; }
    }

    public static class Remind extends Basic {
    }

    public static class Reject extends Basic {
        @NotBlank
        private String targetNodeCode;
        public String getTargetNodeCode() { return targetNodeCode; }
        public void setTargetNodeCode(String targetNodeCode) { this.targetNodeCode = targetNodeCode; }
    }

    public static class Submit extends Basic {
        @NotNull
        private Map<String, Object> variables;
        public Map<String, Object> getVariables() { return variables; }
        public void setVariables(Map<String, Object> variables) { this.variables = variables; }
    }

    public static class DirectSend extends Basic {
        private Map<String, Object> variables;
        public Map<String, Object> getVariables() { return variables; }
        public void setVariables(Map<String, Object> variables) { this.variables = variables; }
    }

    public static class Transfer extends Basic {
        @NotBlank
        private String targetUserId;
        public String getTargetUserId() { return targetUserId; }
        public void setTargetUserId(String targetUserId) { this.targetUserId = targetUserId; }
    }

    public static class Delegate extends Transfer {
        @NotBlank
        private String targetUserName;
        public String getTargetUserName() { return targetUserName; }
        public void setTargetUserName(String targetUserName) { this.targetUserName = targetUserName; }
    }

    public static class AddSign extends Basic {
        @NotEmpty
        private List<@NotBlank String> addSignUserIds;
        public List<String> getAddSignUserIds() { return addSignUserIds; }
        public void setAddSignUserIds(List<String> addSignUserIds) { this.addSignUserIds = addSignUserIds; }
    }
}
