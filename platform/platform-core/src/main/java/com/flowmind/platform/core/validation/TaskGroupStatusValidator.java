package com.flowmind.platform.core.validation;

import java.util.Map;

/**
 * 校验任务组状态、计数和并行分支到达规则。
 *
 * <p>这里表达任务组冻结规则，不替代仓储层的乐观锁和条件更新。</p>
 *
 * @author Yuxin Xu
 * @since 2026-07-15
 */
public class TaskGroupStatusValidator {

    public static final String OR_SIGN = "OR_SIGN";
    public static final String COUNTERSIGN = "COUNTERSIGN";
    public static final String PARALLEL_GATEWAY = "PARALLEL_GATEWAY";
    public static final String ACTIVE = "ACTIVE";
    public static final String COMPLETED = "COMPLETED";
    public static final String CANCELED = "CANCELED";
    public static final String RUNNING = "RUNNING";
    public static final String ARRIVED = "ARRIVED";

    /** 任务组只能从 ACTIVE 进入终态，终态不可回退。 */
    private static final StatusTransitionRules RULES = new StatusTransitionRules()
            .allow(ACTIVE, COMPLETED)
            .allow(ACTIVE, CANCELED);

    public void validateTransition(String sourceStatus, String targetStatus) {
        if (!RULES.allows(sourceStatus, targetStatus)) {
            throw new FrozenValidationException(
                    FrozenValidationErrorCodes.TASK_GROUP_STATUS_TRANSITION_INVALID,
                    "Invalid task group status transition: "
                            + sourceStatus + " -> " + targetStatus + ".");
        }
    }

    public void validateCounts(String groupType,
                               String groupStatus,
                               int totalCount,
                               int completedCount,
                               Map<String, String> branchStates) {
        // 通用计数约束：已完成数不能为负，也不能超过总数。
        if (totalCount < 0 || completedCount < 0 || completedCount > totalCount) {
            throw countInvalid("completed count must be between 0 and total count.");
        }
        // ACTIVE 组如果已经全部完成，应先转为 COMPLETED，不能继续保持活动态。
        if (ACTIVE.equals(groupStatus) && completedCount == totalCount && totalCount > 0) {
            throw countInvalid("active group cannot already have all work completed.");
        }
        // 并行网关组还需要校验分支状态快照与计数一致。
        if (PARALLEL_GATEWAY.equals(groupType)) {
            validateParallelBranchState(totalCount, completedCount, branchStates);
        }
    }

    public void validateBranchArrival(String groupStatus,
                                      int totalCount,
                                      int completedCount,
                                      Map<String, String> branchStates,
                                      String branchKey) {
        // 只有活动中且尚未到齐的任务组可以接收新的分支到达事件。
        if (!ACTIVE.equals(groupStatus) || completedCount >= totalCount) {
            throw branchArrivalInvalid(branchKey);
        }
        // 分支必须存在且处于 RUNNING，重复 ARRIVED 或未知分支都应拒绝。
        if (branchStates == null || !RUNNING.equals(branchStates.get(branchKey))) {
            throw branchArrivalInvalid(branchKey);
        }
    }

    private void validateParallelBranchState(int totalCount,
                                             int completedCount,
                                             Map<String, String> branchStates) {
        // 分支数量必须与 total_count 对齐，否则汇聚计数没有可信基础。
        if (branchStates == null || branchStates.size() != totalCount) {
            throw countInvalid("parallel gateway branch count must equal total count.");
        }
        int arrivedCount = 0;
        for (Map.Entry<String, String> entry : branchStates.entrySet()) {
            String state = entry.getValue();
            if (ARRIVED.equals(state)) {
                arrivedCount++;
            } else if (!RUNNING.equals(state) && !CANCELED.equals(state)) {
                throw countInvalid("parallel gateway branch state is invalid.");
            }
        }
        if (arrivedCount != completedCount) {
            throw countInvalid("parallel gateway arrived branch count must equal completed count.");
        }
    }

    private FrozenValidationException countInvalid(String message) {
        return new FrozenValidationException(
                FrozenValidationErrorCodes.TASK_GROUP_COUNT_INVALID, message);
    }

    private FrozenValidationException branchArrivalInvalid(String branchKey) {
        return new FrozenValidationException(
                FrozenValidationErrorCodes.TASK_GROUP_BRANCH_ARRIVAL_INVALID,
                "Invalid parallel branch arrival: " + branchKey + ".");
    }
}
