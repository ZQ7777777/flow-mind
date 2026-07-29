package com.flowmind.platform.api.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/** Admin diagnostic view for countersign and parallel task groups. */
@Data
public class TaskGroupViewDTO {

    private String groupId;
    private String instanceId;
    private String nodeCode;
    private String joinNodeCode;
    private String parentGroupId;
    private String parentBranchKey;
    private String groupType;
    private Integer totalCount;
    private Integer completedCount;
    private Map<String, Object> branchStates;
    private String groupStatus;
    private Long lockVersion;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
}
