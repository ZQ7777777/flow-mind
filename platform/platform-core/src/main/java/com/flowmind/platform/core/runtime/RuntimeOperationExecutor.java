package com.flowmind.platform.core.runtime;

import com.flowmind.platform.api.dto.TaskActionResult;
import com.flowmind.platform.api.request.OperationRequest;
import com.flowmind.platform.core.definition.OperationIdempotencyDecision;
import com.flowmind.platform.core.definition.OperationIdempotencyDecisionType;
import com.flowmind.platform.core.definition.OperationIdempotencyService;
import com.flowmind.platform.persistence.entity.ProcessOperationRecordEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 运行时幂等动作的统一入口。
 *
 * <p>调用方在业务事务中依次调用 begin、assertExecutable、业务持久化和 markSuccess；因此成功
 * 结果与业务数据由同一外层事务提交。确定性业务失败由调用方显式 markFailed，数据库中断等不确定
 * 故障不应标记 FAILED，以便保留 PROCESSING 租约供恢复流程接管。</p>
 *
 * @author FlowMind
 * @since 2026-07-22
 */
@Component
public class RuntimeOperationExecutor {

    /** 既有的统一幂等记录服务。 */
    private final OperationIdempotencyService idempotencyService;
    /** 请求规范化摘要组件。 */
    private final RuntimeRequestHasher requestHasher;

    @Autowired
    public RuntimeOperationExecutor(OperationIdempotencyService idempotencyService) {
        this(idempotencyService, new RuntimeRequestHasher());
    }

    RuntimeOperationExecutor(OperationIdempotencyService idempotencyService,
                             RuntimeRequestHasher requestHasher) {
        this.idempotencyService = idempotencyService;
        this.requestHasher = requestHasher;
    }

    /**
     * 创建或读取运行时幂等操作决定。
     *
     * @param request    原始运行时请求
     * @param actionType 冻结的运行时动作类型
     * @param operatorId 已经由当前用户校验过的操作人 ID
     * @param instanceId 流程实例目标，可为空
     * @param taskId     活动任务目标，可为空
     * @param now        当前业务时间
     * @return 幂等决策
     */
    public OperationIdempotencyDecision begin(OperationRequest request,
                                              String actionType,
                                              String operatorId,
                                              String instanceId,
                                              String taskId,
                                              LocalDateTime now) {
        if (request == null || isBlank(request.getOperationId()) || isBlank(actionType)
                || isBlank(operatorId) || now == null) {
            throw new RuntimeValidationException(RuntimeErrorCodes.INVALID_ACTION,
                    "operation request, actionType, operatorId and now are required");
        }
        return idempotencyService.beginOrReplay(request.getOperationId(), actionType, operatorId,
                requestHasher.hash(request), instanceId, taskId, now);
    }

    /**
     * 仅允许新建操作或已成功接管过期租约的操作继续执行业务写入。
     *
     * @param decision 幂等决策
     */
    public void assertExecutable(OperationIdempotencyDecision decision) {
        if (decision == null) {
            throw new RuntimeValidationException(RuntimeErrorCodes.INVALID_ACTION, "idempotency decision is required");
        }
        if (OperationIdempotencyDecisionType.NEW.equals(decision.getType())
                || OperationIdempotencyDecisionType.TAKE_OVER.equals(decision.getType())) {
            return;
        }
        if (OperationIdempotencyDecisionType.CONFLICT.equals(decision.getType())) {
            throw new RuntimeValidationException(RuntimeErrorCodes.OPERATION_ID_CONFLICT,
                    "operationId was already used by a different request");
        }
        if (OperationIdempotencyDecisionType.IN_PROGRESS.equals(decision.getType())) {
            throw new RuntimeStateException(RuntimeErrorCodes.OPERATION_IN_PROGRESS,
                    "operation is still processing");
        }
        if (OperationIdempotencyDecisionType.REPLAY_FAILED.equals(decision.getType())) {
            String errorCode = decision.getRecord() == null ? null : decision.getRecord().getErrorCode();
            throw new RuntimeStateException(isBlank(errorCode) ? RuntimeErrorCodes.INVALID_ACTION : errorCode,
                    "operation has a frozen business failure");
        }
        throw new RuntimeStateException(RuntimeErrorCodes.INVALID_ACTION,
                "successful operation must be replayed instead of executed");
    }

    /**
     * 将成功重放记录恢复为任务动作结果，并标记为重放。
     *
     * @param decision 成功重放决定
     * @return 恢复后的任务动作结果
     */
    public TaskActionResult replayTaskAction(OperationIdempotencyDecision decision) {
        ProcessOperationRecordEntity record = requireSuccessfulReplay(decision);
        TaskActionResult result = RuntimeJsonCodec.read(record.getResultJson(), TaskActionResult.class);
        result.setReplayed(true);
        return result;
    }

    /**
     * 将成功重放记录恢复为调用方指定的返回 DTO。
     *
     * @param decision   成功重放决定
     * @param resultType 返回 DTO 类型
     * @param <T>        返回 DTO 泛型
     * @return 恢复后的 DTO
     */
    public <T> T replayResult(OperationIdempotencyDecision decision, Class<T> resultType) {
        return RuntimeJsonCodec.read(requireSuccessfulReplay(decision).getResultJson(), resultType);
    }

    /**
     * 在外层业务事务中保存成功结果。
     *
     * @param operationId 幂等操作号
     * @param result      成功返回 DTO
     */
    public void markSuccess(String operationId, Object result) {
        idempotencyService.markSuccess(operationId, RuntimeJsonCodec.toJson(result));
    }

    /**
     * 仅对已经确定不会重试成功的业务失败冻结错误码。
     *
     * @param operationId 幂等操作号
     * @param errorCode   冻结的业务错误码
     */
    public void markDeterministicFailure(String operationId, String errorCode) {
        if (isBlank(errorCode)) {
            throw new RuntimeValidationException(RuntimeErrorCodes.INVALID_ACTION, "errorCode is required");
        }
        try {
            idempotencyService.markFailed(operationId, errorCode);
        } catch (DataAccessException ex) {
            // 失败标记是辅助幂等状态，不能遮蔽原始业务校验异常。
        }
    }

    /**
     * 为启动类操作在生成实例 ID 后补充目标绑定。
     *
     * @param operationId 幂等操作号
     * @param instanceId  流程实例 ID，可为空
     * @param taskId      活动任务 ID，可为空
     */
    public void bindTarget(String operationId, String instanceId, String taskId) {
        idempotencyService.bindTarget(operationId, instanceId, taskId);
    }

    private ProcessOperationRecordEntity requireSuccessfulReplay(OperationIdempotencyDecision decision) {
        if (decision == null || !OperationIdempotencyDecisionType.REPLAY_SUCCESS.equals(decision.getType())
                || decision.getRecord() == null || isBlank(decision.getRecord().getResultJson())) {
            throw new RuntimeStateException(RuntimeErrorCodes.INVALID_ACTION,
                    "a successful operation replay is required");
        }
        return decision.getRecord();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
