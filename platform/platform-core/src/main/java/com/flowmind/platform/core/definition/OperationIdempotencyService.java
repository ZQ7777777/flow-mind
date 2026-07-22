package com.flowmind.platform.core.definition;

import com.flowmind.platform.api.enums.OperationStatusEnum;
import com.flowmind.platform.persistence.entity.ProcessOperationRecordEntity;
import com.flowmind.platform.persistence.repository.ProcessOperationRecordRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 操作幂等组件，集中维护操作记录的重放校验、租约创建和终态写入。
 *
 * @author Yuxin Xu
 * @since 2026-07-17
 */
@Service
public class OperationIdempotencyService {

    /**
     * PROCESSING 租约时长；超过该时间后可由后续恢复逻辑判断为悬挂操作。
     */
    private static final int PROCESSING_LEASE_MINUTES = 5;

    /**
     * 成功或失败幂等记录保留天数，用于控制重放窗口和清理边界。
     */
    private static final int RECORD_RETENTION_DAYS = 1;

    private final ProcessOperationRecordRepository operationRecordRepository;

    public OperationIdempotencyService(ProcessOperationRecordRepository operationRecordRepository) {
        this.operationRecordRepository = operationRecordRepository;
    }

    /**
     * 查询并校验可重放的幂等记录。
     *
     * @param operationId 客户端传入的幂等操作号，同一个业务请求重试时必须保持不变
     * @param actionType  操作动作类型，用于阻止同一个 operationId 跨 create/save/copy/delete 复用
     * @param requestHash 规范化后的请求摘要，用于判断同一个 operationId 是否对应完全相同的请求内容
     * @return 不存在时返回 null，存在且可重放时返回记录
     */
    public ProcessOperationRecordEntity replay(String operationId, String actionType, String requestHash) {
        ProcessOperationRecordEntity existing = operationRecordRepository.findByOperationId(operationId);
        if (existing == null) {
            return null;
        }
        validateReplay(existing, actionType, requestHash);
        return existing;
    }

    /**
     * 创建 PROCESSING 状态的幂等记录。
     *
     * @param operationId 客户端传入的幂等操作号，作为幂等记录唯一键
     * @param actionType  操作动作类型，和 requestHash 一起定义幂等冲突语义
     * @param operatorId  操作人 ID，只用于审计记录，不参与幂等冲突判断
     * @param requestHash 规范化后的请求摘要，后续重放时必须与记录中的摘要一致
     * @param now         业务操作开始时间，用于派生 PROCESSING 租约时间和记录保留时间
     * @return 已插入的操作记录实体
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ProcessOperationRecordEntity begin(String operationId,
                                              String actionType,
                                              String operatorId,
                                              String requestHash,
                                              LocalDateTime now) {
        return begin(operationId, null, null, actionType, operatorId, requestHash, now);
    }

    /**
     * 创建带运行期实例和任务上下文的 PROCESSING 幂等记录。
     *
     * @param operationId 客户端传入的幂等操作号，作为幂等记录唯一键
     * @param instanceId  运行期实例 ID；定义期操作或实例创建前可为空
     * @param taskId      运行期任务 ID；实例级动作可为空
     * @param actionType  操作动作类型
     * @param operatorId  操作人 ID
     * @param requestHash 规范化后的请求摘要
     * @param now         业务操作开始时间
     * @return 已插入的操作记录实体
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ProcessOperationRecordEntity begin(String operationId,
                                              String instanceId,
                                              String taskId,
                                              String actionType,
                                              String operatorId,
                                              String requestHash,
                                              LocalDateTime now) {
        ProcessOperationRecordEntity operation = new ProcessOperationRecordEntity();
        operation.setId(newId());
        operation.setOperationId(operationId);
        operation.setInstanceId(instanceId);
        operation.setTaskId(taskId);
        operation.setActionType(actionType);
        operation.setOperatorId(operatorId);
        operation.setRequestHash(requestHash);
        operation.setOperationStatus(OperationStatusEnum.PROCESSING.name());
        operation.setProcessingExpiresAt(now.plusMinutes(PROCESSING_LEASE_MINUTES));
        operation.setExpiresAt(now.plusDays(RECORD_RETENTION_DAYS));
        operation.setCreatedAt(now);
        operation.setUpdatedAt(now);
        operationRecordRepository.insert(operation);
        return operation;
    }

    /**
     * 将幂等记录标记为成功并保存重放结果。
     *
     * @param operationId 客户端幂等操作号
     * @param resultJson  成功结果 JSON 对象，重放时从该字段恢复业务返回值
     */
    public void markSuccess(String operationId, String resultJson) {
        operationRecordRepository.markSuccess(operationId, resultJson);
    }

    /**
     * 将幂等记录标记为确定性失败并保存错误码。
     *
     * @param operationId 客户端幂等操作号
     * @param errorCode   确定性失败错误码，调用方可据此区分业务失败与处理中状态
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(String operationId, String errorCode) {
        operationRecordRepository.markFailed(operationId, errorCode);
    }

    /**
     * 创建或判断幂等操作记录，完整覆盖 M0 冻结的 begin/replay/lease 决策语义。
     *
     * @param operationId 客户端传入的幂等操作号
     * @param actionType  操作动作类型
     * @param operatorId  操作人 ID
     * @param requestHash 规范化后的请求摘要
     * @param now         当前业务时间
     * @return 幂等决策结果
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public OperationIdempotencyDecision beginOrReplay(String operationId,
                                                      String actionType,
                                                      String operatorId,
                                                      String requestHash,
                                                      LocalDateTime now) {
        return beginOrReplay(operationId, null, null, actionType, operatorId, requestHash, now);
    }

    /**
     * 创建或判断带运行期上下文的幂等操作记录。
     *
     * @param operationId 客户端传入的幂等操作号
     * @param instanceId  运行期实例 ID；定义期操作或实例创建前可为空
     * @param taskId      运行期任务 ID；实例级动作可为空
     * @param actionType  操作动作类型
     * @param operatorId  操作人 ID
     * @param requestHash 规范化后的请求摘要
     * @param now         当前业务时间
     * @return 幂等决策结果
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public OperationIdempotencyDecision beginOrReplay(String operationId,
                                                      String instanceId,
                                                      String taskId,
                                                      String actionType,
                                                      String operatorId,
                                                      String requestHash,
                                                      LocalDateTime now) {
        ProcessOperationRecordEntity existing = operationRecordRepository.findByOperationId(operationId);
        if (existing == null) {
            try {
                return new OperationIdempotencyDecision(OperationIdempotencyDecisionType.NEW,
                        begin(operationId, instanceId, taskId, actionType, operatorId, requestHash, now));
            } catch (DataIntegrityViolationException ex) {
                existing = operationRecordRepository.findByOperationId(operationId);
                if (existing == null) {
                    throw ex;
                }
            }
        }
        return decideExisting(existing, actionType, requestHash, now);
    }

    private OperationIdempotencyDecision decideExisting(ProcessOperationRecordEntity existing,
                                                        String actionType,
                                                        String requestHash,
                                                        LocalDateTime now) {
        if (!actionType.equals(existing.getActionType()) || !requestHash.equals(existing.getRequestHash())) {
            return new OperationIdempotencyDecision(OperationIdempotencyDecisionType.CONFLICT, existing);
        }
        if (OperationStatusEnum.SUCCESS.name().equals(existing.getOperationStatus())) {
            return new OperationIdempotencyDecision(OperationIdempotencyDecisionType.REPLAY_SUCCESS, existing);
        }
        if (OperationStatusEnum.FAILED.name().equals(existing.getOperationStatus())) {
            return new OperationIdempotencyDecision(OperationIdempotencyDecisionType.REPLAY_FAILED, existing);
        }
        if (existing.getProcessingExpiresAt() != null && existing.getProcessingExpiresAt().isAfter(now)) {
            return new OperationIdempotencyDecision(OperationIdempotencyDecisionType.IN_PROGRESS, existing);
        }

        operationRecordRepository.extendProcessingLease(operationId, now.plusMinutes(PROCESSING_LEASE_MINUTES));
        ProcessOperationRecordEntity takenOver = operationRecordRepository.findByOperationId(operationId);
        return new OperationIdempotencyDecision(OperationIdempotencyDecisionType.TAKE_OVER, takenOver);
    }

    /**
     * 校验已存在记录是否允许重放。
     *
     * @param existing    数据库中已有的幂等记录
     * @param actionType  当前请求动作类型，必须与已有记录一致
     * @param requestHash 当前请求摘要，必须与已有记录一致
     */
    private void validateReplay(ProcessOperationRecordEntity existing, String actionType, String requestHash) {
        if (!actionType.equals(existing.getActionType())) {
            throw new DefinitionValidationException(DefinitionErrorCodes.OPERATION_ID_CONFLICT,
                    "operationId already exists with different action");
        }
        if (!requestHash.equals(existing.getRequestHash())) {
            throw new DefinitionValidationException(DefinitionErrorCodes.OPERATION_ID_CONFLICT,
                    "operationId already exists with different request");
        }
        if (!OperationStatusEnum.SUCCESS.name().equals(existing.getOperationStatus())) {
            String errorCode = OperationStatusEnum.FAILED.name().equals(existing.getOperationStatus())
                    ? existing.getErrorCode() : DefinitionErrorCodes.OPERATION_IN_PROGRESS;
            throw new DefinitionStateException(errorCode, "operation is still processing or failed");
        }
    }

    private String newId() {
        return UUID.randomUUID().toString();
    }
}
