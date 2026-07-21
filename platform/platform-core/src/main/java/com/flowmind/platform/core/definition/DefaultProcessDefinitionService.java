package com.flowmind.platform.core.definition;

import com.flowmind.platform.api.dto.OperationResult;
import com.flowmind.platform.api.dto.PageResult;
import com.flowmind.platform.api.dto.ProcessAttachmentConfigDTO;
import com.flowmind.platform.api.dto.ProcessDefinitionDTO;
import com.flowmind.platform.api.dto.ProcessDefinitionDetailDTO;
import com.flowmind.platform.api.dto.ProcessDefinitionQuery;
import com.flowmind.platform.api.dto.ProcessEdgeDTO;
import com.flowmind.platform.api.dto.ProcessFormFieldDTO;
import com.flowmind.platform.api.dto.ProcessNodeDTO;
import com.flowmind.platform.api.dto.ValidationResult;
import com.flowmind.platform.api.enums.ActivationStatusEnum;
import com.flowmind.platform.api.enums.DefinitionActionTypeEnum;
import com.flowmind.platform.api.enums.DefinitionStatusEnum;
import com.flowmind.platform.api.enums.GrayStatusEnum;
import com.flowmind.platform.api.enums.OperationTargetTypeEnum;
import com.flowmind.platform.api.request.CopyProcessDefinitionRequest;
import com.flowmind.platform.api.request.CreateProcessDefinitionRequest;
import com.flowmind.platform.api.request.DefinitionOperationRequest;
import com.flowmind.platform.api.request.GrayReleaseRequest;
import com.flowmind.platform.api.request.SaveProcessGraphRequest;
import com.flowmind.platform.api.service.ProcessDefinitionService;
import com.flowmind.platform.core.validation.DefinitionModelValidator;
import com.flowmind.platform.core.validation.DefinitionRequestValidator;
import com.flowmind.platform.core.validation.DefinitionStatusValidator;
import com.flowmind.platform.persistence.entity.ProcessDefinitionAttachmentConfigEntity;
import com.flowmind.platform.persistence.entity.ProcessDefinitionEntity;
import com.flowmind.platform.persistence.entity.ProcessEdgeEntity;
import com.flowmind.platform.persistence.entity.ProcessFormFieldEntity;
import com.flowmind.platform.persistence.entity.ProcessNodeEntity;
import com.flowmind.platform.persistence.entity.ProcessOperationRecordEntity;
import com.flowmind.platform.persistence.repository.ProcessDefinitionAttachmentConfigRepository;
import com.flowmind.platform.persistence.repository.ProcessDefinitionRepository;
import com.flowmind.platform.persistence.repository.ProcessEdgeRepository;
import com.flowmind.platform.persistence.repository.ProcessFormFieldRepository;
import com.flowmind.platform.persistence.repository.ProcessNodeRepository;
import com.flowmind.platform.persistence.repository.ProcessOperationRecordRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 默认流程定义管理服务实现。
 *
 * @author Yuxin Xu
 * @since 2026-07-17
 */
@Service
public class DefaultProcessDefinitionService implements ProcessDefinitionService {

    private static final int COPY_VERSION_RETRY_LIMIT = 3;

    private final ProcessDefinitionRepository definitionRepository;
    private final ProcessNodeRepository nodeRepository;
    private final ProcessEdgeRepository edgeRepository;
    private final ProcessFormFieldRepository formFieldRepository;
    private final ProcessDefinitionAttachmentConfigRepository attachmentConfigRepository;
    private final OperationIdempotencyService idempotencyService;
    private final ProcessDefinitionCache processDefinitionCache;
    private final DefinitionStatusValidator statusValidator = new DefinitionStatusValidator();
    private final DefinitionModelValidator modelValidator = new DefinitionModelValidator();
    private final DefinitionGraphDraftFactory graphDraftFactory = new DefinitionGraphDraftFactory();

    /**
     * 默认构造器，装配定义管理所需的持久化仓储、幂等组件和定义图缓存。
     *
     * @param definitionRepository      流程定义主表仓储
     * @param nodeRepository            流程节点仓储
     * @param edgeRepository            流程连线仓储
     * @param formFieldRepository       流程表单字段仓储
     * @param attachmentConfigRepository 流程定义附件配置仓储
     * @param idempotencyService        操作幂等组件，负责 begin/replay/markSuccess/markFailed
     * @param processDefinitionCache    定义图缓存组件，为空时使用本地默认缓存
     */
    @Autowired
    public DefaultProcessDefinitionService(ProcessDefinitionRepository definitionRepository,
                                           ProcessNodeRepository nodeRepository,
                                           ProcessEdgeRepository edgeRepository,
                                           ProcessFormFieldRepository formFieldRepository,
                                           ProcessDefinitionAttachmentConfigRepository attachmentConfigRepository,
                                           OperationIdempotencyService idempotencyService,
                                           ProcessDefinitionCache processDefinitionCache) {
        this.definitionRepository = definitionRepository;
        this.nodeRepository = nodeRepository;
        this.edgeRepository = edgeRepository;
        this.formFieldRepository = formFieldRepository;
        this.attachmentConfigRepository = attachmentConfigRepository;
        this.idempotencyService = idempotencyService;
        this.processDefinitionCache = processDefinitionCache == null
                ? new ProcessDefinitionCache() : processDefinitionCache;
    }

    public DefaultProcessDefinitionService(ProcessDefinitionRepository definitionRepository,
                                           ProcessNodeRepository nodeRepository,
                                           ProcessEdgeRepository edgeRepository,
                                           ProcessFormFieldRepository formFieldRepository,
                                           ProcessDefinitionAttachmentConfigRepository attachmentConfigRepository,
                                           ProcessOperationRecordRepository operationRecordRepository,
                                           ProcessDefinitionCache processDefinitionCache) {
        this(definitionRepository, nodeRepository, edgeRepository, formFieldRepository, attachmentConfigRepository,
                new OperationIdempotencyService(operationRecordRepository), processDefinitionCache);
    }

    public DefaultProcessDefinitionService(ProcessDefinitionRepository definitionRepository,
                                           ProcessNodeRepository nodeRepository,
                                           ProcessEdgeRepository edgeRepository,
                                           ProcessFormFieldRepository formFieldRepository,
                                           ProcessDefinitionAttachmentConfigRepository attachmentConfigRepository,
                                           ProcessOperationRecordRepository operationRecordRepository) {
        this(definitionRepository, nodeRepository, edgeRepository, formFieldRepository, attachmentConfigRepository,
                operationRecordRepository, new ProcessDefinitionCache());
    }

    /**
     * 创建流程定义
     * @param request 创建请求
     * @return ProcessDefinitionDTO
     */
    @Override
    @Transactional
    public ProcessDefinitionDTO createDefinition(CreateProcessDefinitionRequest request) {
        //校验请求是否合法
        DefinitionRequestValidator.validateCreate(request);
        String requestHash = hashCreateRequest(request);
        //获取业务操作类型
        String actionType = DefinitionActionTypeEnum.CREATE.getOperationActionType();
        LocalDateTime now = LocalDateTime.now();
        //检查当前请求是否第一次执行，如果以前已经成功，返回之前的结果；如果有冲突则返回对应的决策
        OperationIdempotencyDecision decision = idempotencyService.beginOrReplay(
                request.getOperationId(), actionType, request.getOperatorUserId(), requestHash, now);
        //执行幂等操作
        if (OperationIdempotencyDecisionType.REPLAY_SUCCESS.equals(decision.getType())) {
            return replayCreateDefinition(decision.getRecord());
        }
        rejectNonExecutableOperation(decision);

        //查询最大版本号，如果没有旧版本，则新创建的定义版本号是1
        Integer maxVersion = definitionRepository.findMaxVersionByProcessCode(request.getProcessCode());
        ProcessDefinitionEntity entity = new ProcessDefinitionEntity();
        entity.setId(newId());
        entity.setProcessCode(request.getProcessCode());
        entity.setProcessName(request.getProcessName());
        entity.setSystemCode(request.getSystemCode());
        entity.setVersion(Integer.valueOf(maxVersion == null ? 1 : maxVersion.intValue() + 1));
        entity.setDefinitionStatus(DefinitionStatusEnum.DRAFT.name());
        entity.setActivationStatus(ActivationStatusEnum.INACTIVE.name());
        entity.setGrayStatus(GrayStatusEnum.OFF.name());
        entity.setRemark(request.getRemark());
        entity.setCreatedBy(request.getOperatorUserId());
        entity.setCreatedAt(now);
        entity.setUpdatedBy(request.getOperatorUserId());
        entity.setUpdatedAt(now);
        //将实体类存到数据库
        definitionRepository.insert(entity);

        //将幂等操作标记为成功
        idempotencyService.markSuccess(request.getOperationId(), JsonCodec.definitionResult(entity.getId()));
        return ProcessDefinitionMapper.toDto(entity);
    }

    /**
     * 保存流程图结构
     * @param definitionId 流程定义 ID
     * @param request 保存请求
     * @return
     */
    @Override
    @Transactional
    public ProcessDefinitionDTO saveGraph(String definitionId, SaveProcessGraphRequest request) {
        if (!hasText(definitionId)) {
            throw new DefinitionValidationException(DefinitionErrorCodes.DEFINITION_INVALID,
                    "definitionId must not be empty");
        }
        DefinitionRequestValidator.validateSaveGraph(request);

        //校验流程定义是否可编辑
        ProcessDefinitionEntity definition = definitionRepository.findById(definitionId);
        if (definition == null) {
            throw new DefinitionValidationException(DefinitionErrorCodes.DEFINITION_NOT_FOUND,
                    "definition not found: " + definitionId);
        }
        statusValidator.validateEditable(definition.getDefinitionStatus(),
                definition.getActivationStatus(), definition.getGrayStatus());

        //归一化节点、连线、表单字段和附件配置，避免前端传入数据缺字段导致哈希计算有误
        LocalDateTime now = LocalDateTime.now();
        List<ProcessNodeDTO> nodes = graphDraftFactory.normalizeNodes(definitionId, request.getNodes());
        List<ProcessEdgeDTO> edges = graphDraftFactory.normalizeEdges(definitionId, request.getEdges());
        List<ProcessFormFieldDTO> formFields =
                graphDraftFactory.normalizeFormFields(definitionId, request.getFormFields());
        List<ProcessAttachmentConfigDTO> attachmentConfigs =
                graphDraftFactory.normalizeAttachmentConfigs(definitionId, request.getAttachmentConfigs());

        // 先归一化再计算摘要，确保空 ID、默认排序、默认布尔值不会让同一业务请求产生不同幂等 hash。
        String requestHash = hashSaveGraph(definitionId, request, nodes, edges, formFields, attachmentConfigs);
        String actionType = DefinitionActionTypeEnum.SAVE_GRAPH.getOperationActionType();
        //开始或重放幂等操作
        OperationIdempotencyDecision decision = idempotencyService.beginOrReplay(
                request.getOperationId(), actionType, request.getOperatorUserId(), requestHash, now);
        if (OperationIdempotencyDecisionType.REPLAY_SUCCESS.equals(decision.getType())) {
            return replaySaveGraph(decision.getRecord());
        }
        rejectNonExecutableOperation(decision);
        modelValidator.validateSaveGraphStructure(nodes, edges);

        //删除全部旧图元素，批量插入新图元素
        edgeRepository.deleteByDefinitionId(definitionId);
        nodeRepository.deleteByDefinitionId(definitionId);
        nodeRepository.batchInsert(ProcessDefinitionMapper.toNodeEntities(nodes));
        edgeRepository.batchInsert(ProcessDefinitionMapper.toEdgeEntities(edges));
        definitionRepository.touchUpdated(definitionId, request.getOperatorUserId());
        formFieldRepository.deleteByDefinitionId(definitionId);
        formFieldRepository.batchInsert(ProcessDefinitionMapper.toFormFieldEntities(formFields));
        attachmentConfigRepository.deleteByDefinitionId(definitionId);
        attachmentConfigRepository.batchInsert(graphDraftFactory.toDraftAttachmentConfigEntities(
                attachmentConfigs, request.getOperatorUserId(), now));

        //标记幂等操作成功
        idempotencyService.markSuccess(request.getOperationId(), JsonCodec.definitionResult(definitionId));
        //注册流程图缓存失效
        registerGraphCacheInvalidation(definition.getId());
        return ProcessDefinitionMapper.toDto(definitionRepository.findById(definitionId));
    }

    @Override
    public ValidationResult validateForPublish(String definitionId) {
        throw new UnsupportedOperationException("validateForPublish is not implemented in M1.3");
    }

    @Override
    public ProcessDefinitionDTO publish(DefinitionOperationRequest request) {
        throw new UnsupportedOperationException("publish is not implemented in M1.3");
    }

    @Override
    public ProcessDefinitionDTO activate(DefinitionOperationRequest request) {
        throw new UnsupportedOperationException("activate is not implemented in M1.3");
    }

    @Override
    public ProcessDefinitionDTO deactivate(DefinitionOperationRequest request) {
        throw new UnsupportedOperationException("deactivate is not implemented in M1.3");
    }

    @Override
    public ProcessDefinitionDTO archive(DefinitionOperationRequest request) {
        throw new UnsupportedOperationException("archive is not implemented in M1.3");
    }

    @Override
    public ProcessDefinitionDTO enableGray(GrayReleaseRequest request) {
        throw new UnsupportedOperationException("enableGray is not implemented in M1.3");
    }

    @Override
    public ProcessDefinitionDTO disableGray(DefinitionOperationRequest request) {
        throw new UnsupportedOperationException("disableGray is not implemented in M1.3");
    }

    @Override
    @Transactional
    public ProcessDefinitionDTO copyDefinition(String definitionId, CopyProcessDefinitionRequest request) {
        if (!hasText(definitionId)) {
            throw new DefinitionValidationException(DefinitionErrorCodes.DEFINITION_INVALID,
                    "definitionId must not be empty");
        }
        DefinitionRequestValidator.validateCopy(request);
        String requestHash = hashCopyRequest(definitionId, request);
        String actionType = DefinitionActionTypeEnum.COPY.getOperationActionType();
        LocalDateTime now = LocalDateTime.now();
        OperationIdempotencyDecision decision = idempotencyService.beginOrReplay(
                request.getOperationId(), actionType, request.getOperatorUserId(), requestHash, now);
        if (OperationIdempotencyDecisionType.REPLAY_SUCCESS.equals(decision.getType())) {
            return replayCopyDefinition(decision.getRecord());
        }
        rejectNonExecutableOperation(decision);

        ProcessDefinitionEntity source = definitionRepository.findById(definitionId);
        if (source == null) {
            throw new DefinitionValidationException(DefinitionErrorCodes.DEFINITION_NOT_FOUND,
                    "definition not found: " + definitionId);
        }
        List<ProcessNodeEntity> sourceNodes = nodeRepository.findByDefinitionId(definitionId);
        List<ProcessEdgeEntity> sourceEdges = edgeRepository.findByDefinitionId(definitionId);
        List<ProcessFormFieldEntity> sourceFormFields = formFieldRepository.findByDefinitionId(definitionId);
        List<ProcessDefinitionAttachmentConfigEntity> sourceAttachmentConfigs =
                attachmentConfigRepository.findByDefinitionId(definitionId);

        ProcessDefinitionEntity copied = insertCopiedDefinitionWithRetry(source, request, now);
        nodeRepository.batchInsert(graphDraftFactory.copyNodeEntities(sourceNodes, copied.getId()));
        edgeRepository.batchInsert(graphDraftFactory.copyEdgeEntities(sourceEdges, copied.getId()));
        formFieldRepository.batchInsert(graphDraftFactory.copyFormFieldEntities(sourceFormFields, copied.getId()));
        attachmentConfigRepository.batchInsert(graphDraftFactory.copyAttachmentConfigEntities(sourceAttachmentConfigs,
                copied.getId(), request.getOperatorUserId(), now));

        idempotencyService.markSuccess(request.getOperationId(), JsonCodec.definitionResult(copied.getId()));
        registerGraphCacheInvalidation(copied.getId());
        return ProcessDefinitionMapper.toDto(copied);
    }

    @Override
    @Transactional
    public OperationResult deleteDefinition(DefinitionOperationRequest request) {
        DefinitionRequestValidator.validateDefinitionOperation(request);
        String requestHash = hashDeleteRequest(request);
        String actionType = DefinitionActionTypeEnum.DELETE.getOperationActionType();
        LocalDateTime now = LocalDateTime.now();
        OperationIdempotencyDecision decision = idempotencyService.beginOrReplay(
                request.getOperationId(), actionType, request.getOperatorUserId(), requestHash, now);
        if (OperationIdempotencyDecisionType.REPLAY_SUCCESS.equals(decision.getType())) {
            return replayDeleteDefinition(decision.getRecord());
        }
        rejectNonExecutableOperation(decision);

        ProcessDefinitionEntity definition = definitionRepository.findById(request.getDefinitionId());
        if (definition == null) {
            throw new DefinitionValidationException(DefinitionErrorCodes.DEFINITION_NOT_FOUND,
                    "definition not found: " + request.getDefinitionId());
        }
        if (ActivationStatusEnum.ACTIVE.name().equals(definition.getActivationStatus())) {
            throw new DefinitionStateException(DefinitionErrorCodes.DEFINITION_NOT_EDITABLE,
                    "active definition must not be deleted");
        }

        formFieldRepository.deleteByDefinitionId(definition.getId());
        attachmentConfigRepository.deleteByDefinitionId(definition.getId());
        definitionRepository.deleteAttachmentsByDefinitionId(definition.getId());
        definitionRepository.deleteReadRecordsByDefinitionId(definition.getId());
        definitionRepository.deleteHistoryTasksByDefinitionId(definition.getId());
        definitionRepository.deleteActiveTasksByDefinitionId(definition.getId());
        definitionRepository.deleteTaskGroupsByDefinitionId(definition.getId());
        definitionRepository.deleteReminderRecordsByDefinitionId(definition.getId());
        definitionRepository.deleteAlertRecordsByDefinitionId(definition.getId());
        definitionRepository.deleteInstancesByDefinitionId(definition.getId());
        edgeRepository.deleteByDefinitionId(definition.getId());
        nodeRepository.deleteByDefinitionId(definition.getId());
        definitionRepository.deleteById(definition.getId());

        idempotencyService.markSuccess(request.getOperationId(), JsonCodec.deleteResult(definition.getId()));
        registerGraphCacheInvalidation(definition.getId());
        return deleteOperationResult(request.getOperationId(), definition.getId(), false);
    }

    /**
     * 查询流程定义详情。
     *
     * @param definitionId 流程定义 ID
     * @return 流程定义详情
     */
    @Override
    public ProcessDefinitionDetailDTO getDefinition(String definitionId) {
        if (!hasText(definitionId)) {
            throw new IllegalArgumentException("definitionId must not be empty");
        }
        ProcessDefinitionEntity definition = definitionRepository.findById(definitionId);
        if (definition == null) {
            return null;
        }
        return ProcessDefinitionMapper.toDetailDto(definition,
                nodeRepository.findByDefinitionId(definitionId),
                edgeRepository.findByDefinitionId(definitionId),
                formFieldRepository.findByDefinitionId(definitionId),
                attachmentConfigRepository.findByDefinitionId(definitionId));
    }

    /**
     * 分页查询流程定义。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    @Override
    public PageResult<ProcessDefinitionDTO> searchDefinitions(ProcessDefinitionQuery query) {
        ProcessDefinitionQuery normalized = DefinitionRequestValidator.normalizeQuery(query);
        long total = definitionRepository.countByQuery(normalized);
        List<ProcessDefinitionEntity> entities = definitionRepository.searchByQuery(normalized);
        List<ProcessDefinitionDTO> records = new ArrayList<ProcessDefinitionDTO>(entities.size());
        for (ProcessDefinitionEntity entity : entities) {
            records.add(ProcessDefinitionMapper.toDto(entity));
        }

        PageResult<ProcessDefinitionDTO> result = new PageResult<ProcessDefinitionDTO>();
        result.setRecords(records);
        result.setPageNo(normalized.getPageNo());
        result.setPageSize(normalized.getPageSize());
        result.setTotal(Long.valueOf(total));
        result.setTotalPages(Integer.valueOf(totalPages(total, normalized.getPageSize().intValue())));
        return result;
    }

    /**
     * 查询流程定义节点列表。
     *
     * @param definitionId 流程定义 ID
     * @return 节点列表
     */
    @Override
    public List<ProcessNodeDTO> listNodes(String definitionId) {
        if (!hasText(definitionId)) {
            throw new IllegalArgumentException("definitionId must not be empty");
        }
        return ProcessDefinitionMapper.toNodeDtos(nodeRepository.findByDefinitionId(definitionId));
    }

    /**
     * 插入复制后的定义主表记录，并在版本唯一键冲突时重新取最新版本号。
     *
     * @param source  源流程定义，提供 processCode 和默认展示信息
     * @param request 复制请求，提供操作人和可覆盖的名称、系统编码、备注
     * @param now     当前业务时间，用于复制记录的 createdAt/updatedAt
     * @return 插入成功的新流程定义实体
     */
    private ProcessDefinitionEntity insertCopiedDefinitionWithRetry(ProcessDefinitionEntity source,
                                                                    CopyProcessDefinitionRequest request,
                                                                    LocalDateTime now) {
        DataAccessException lastConflict = null;
        for (int attempt = 0; attempt < COPY_VERSION_RETRY_LIMIT; attempt++) {
            Integer maxVersion = definitionRepository.findMaxVersionByProcessCode(source.getProcessCode());
            ProcessDefinitionEntity copied = buildCopiedDefinition(source, request, maxVersion, now);
            try {
                definitionRepository.insert(copied);
                return copied;
            } catch (DataAccessException ex) {
                if (!isDefinitionVersionConflict(ex) || attempt == COPY_VERSION_RETRY_LIMIT - 1) {
                    throw ex;
                }
                lastConflict = ex;
            }
        }
        throw lastConflict == null
                ? new IllegalStateException("copy definition version allocation failed")
                : lastConflict;
    }

    private ProcessDefinitionEntity buildCopiedDefinition(ProcessDefinitionEntity source,
                                                          CopyProcessDefinitionRequest request,
                                                          Integer maxVersion,
                                                          LocalDateTime now) {
        ProcessDefinitionEntity copied = new ProcessDefinitionEntity();
        copied.setId(newId());
        copied.setProcessCode(source.getProcessCode());
        copied.setProcessName(hasText(request.getProcessName())
                ? request.getProcessName().trim() : source.getProcessName());
        copied.setSystemCode(hasText(request.getSystemCode())
                ? request.getSystemCode().trim() : source.getSystemCode());
        copied.setVersion(Integer.valueOf(maxVersion == null ? 1 : maxVersion.intValue() + 1));
        copied.setDefinitionStatus(DefinitionStatusEnum.DRAFT.name());
        copied.setActivationStatus(ActivationStatusEnum.INACTIVE.name());
        copied.setGrayStatus(GrayStatusEnum.OFF.name());
        copied.setGrayRuleConfig(null);
        copied.setArchivedBy(null);
        copied.setArchivedAt(null);
        copied.setRemark(request.getRemark() == null ? source.getRemark() : request.getRemark());
        copied.setCreatedBy(request.getOperatorUserId());
        copied.setCreatedAt(now);
        copied.setUpdatedBy(request.getOperatorUserId());
        copied.setUpdatedAt(now);
        return copied;
    }

    private boolean isDefinitionVersionConflict(DataAccessException ex) {
        String message = ex.getMessage();
        return message != null
                && message.contains("process_definition")
                && message.contains("process_code")
                && message.contains("version");
    }

    private ProcessDefinitionDTO replaySaveGraph(ProcessOperationRecordEntity existing) {
        String definitionId = JsonCodec.extractDefinitionId(existing.getResultJson());
        ProcessDefinitionEntity entity = definitionRepository.findById(definitionId);
        if (entity == null) {
            throw new IllegalStateException("idempotent definition result not found");
        }
        return ProcessDefinitionMapper.toDto(entity);
    }

    private void rejectNonExecutableOperation(OperationIdempotencyDecision decision) {
        if (OperationIdempotencyDecisionType.NEW.equals(decision.getType())
                || OperationIdempotencyDecisionType.TAKE_OVER.equals(decision.getType())) {
            return;
        }
        if (OperationIdempotencyDecisionType.CONFLICT.equals(decision.getType())) {
            throw new DefinitionValidationException(DefinitionErrorCodes.OPERATION_ID_CONFLICT,
                    "operationId already exists with different action or request");
        }
        if (OperationIdempotencyDecisionType.IN_PROGRESS.equals(decision.getType())) {
            throw new DefinitionStateException(DefinitionErrorCodes.OPERATION_IN_PROGRESS,
                    "operation is still processing");
        }
        if (OperationIdempotencyDecisionType.REPLAY_FAILED.equals(decision.getType())) {
            String errorCode = decision.getRecord() == null || !hasText(decision.getRecord().getErrorCode())
                    ? DefinitionErrorCodes.DEFINITION_INVALID : decision.getRecord().getErrorCode();
            throw new DefinitionStateException(errorCode, "operation has already failed: " + errorCode);
        }
    }

    private ProcessDefinitionDTO replayCopyDefinition(ProcessOperationRecordEntity existing) {
        String definitionId = JsonCodec.extractDefinitionId(existing.getResultJson());
        ProcessDefinitionEntity entity = definitionRepository.findById(definitionId);
        if (entity == null) {
            throw new IllegalStateException("idempotent definition result not found");
        }
        return ProcessDefinitionMapper.toDto(entity);
    }

    private OperationResult replayDeleteDefinition(ProcessOperationRecordEntity existing) {
        return deleteOperationResult(existing.getOperationId(),
                JsonCodec.extractDefinitionId(existing.getResultJson()), true);
    }

    private ProcessDefinitionDTO replayCreateDefinition(ProcessOperationRecordEntity existing) {
        String definitionId = JsonCodec.extractDefinitionId(existing.getResultJson());
        ProcessDefinitionEntity entity = definitionRepository.findById(definitionId);
        if (entity == null) {
            throw new IllegalStateException("idempotent definition result not found");
        }
        return ProcessDefinitionMapper.toDto(entity);
    }

    /**
     * 为请求创建哈希
     * @param request
     * @return
     */
    private String hashCreateRequest(CreateProcessDefinitionRequest request) {
        return sha256("CREATE|"
                + safe(request.getOperatorUserId()) + "|"
                + safe(request.getProcessCode()) + "|"
                + safe(request.getProcessName()) + "|"
                + safe(request.getSystemCode()) + "|"
                + safe(request.getRemark()));
    }

    private String hashCopyRequest(String definitionId, CopyProcessDefinitionRequest request) {
        StringBuilder builder = new StringBuilder("COPY|");
        appendPart(builder, definitionId);
        appendPart(builder, request.getOperatorUserId());
        appendPart(builder, trimToNull(request.getProcessName()));
        appendPart(builder, trimToNull(request.getSystemCode()));
        appendPart(builder, request.getRemark());
        return sha256(builder.toString());
    }

    private String hashDeleteRequest(DefinitionOperationRequest request) {
        StringBuilder builder = new StringBuilder("DELETE|");
        appendPart(builder, request.getDefinitionId());
        appendPart(builder, request.getOperatorUserId());
        return sha256(builder.toString());
    }

    /**
     * 计算保存流程图的幂等摘要。
     *
     * @param definitionId       当前定义 ID，防止同一个 operationId 在不同定义间误重放
     * @param request            原始保存请求，提供 operationId 之外的操作人等请求级信息
     * @param nodes              已归一化节点列表，摘要使用补齐默认值后的内容
     * @param edges              已归一化连线列表，摘要使用补齐默认值后的内容
     * @param formFields         已归一化表单字段列表，空列表和 null 会被统一为同一种语义
     * @param attachmentConfigs  已归一化附件配置列表，包含默认附件配置业务号和数量下限
     * @return 可用于幂等冲突判断的 SHA-256 摘要
     */
    private String hashSaveGraph(String definitionId,
                                 SaveProcessGraphRequest request,
                                 List<ProcessNodeDTO> nodes,
                                 List<ProcessEdgeDTO> edges,
                                 List<ProcessFormFieldDTO> formFields,
                                 List<ProcessAttachmentConfigDTO> attachmentConfigs) {
        StringBuilder builder = new StringBuilder("SAVE_GRAPH|");
        appendPart(builder, definitionId);
        appendPart(builder, request.getOperatorUserId());
        appendPart(builder, Integer.valueOf(nodes.size()));
        for (ProcessNodeDTO node : nodes) {
            appendPart(builder, node.getNodeCode());
            appendPart(builder, node.getNodeName());
            appendPart(builder, node.getNodeType());
            appendPart(builder, node.getPairedGatewayCode());
            appendPart(builder, node.getApproverRuleType());
            appendPart(builder, node.getApproverRuleConfig());
            appendPart(builder, node.getMultiInstanceMode());
            appendPart(builder, node.getListenerConfig());
            appendPart(builder, node.getTimeoutConfig());
            appendPart(builder, node.getReminderConfig());
            appendPart(builder, node.getPositionX());
            appendPart(builder, node.getPositionY());
            appendPart(builder, node.getSortOrder());
        }
        appendPart(builder, Integer.valueOf(edges.size()));
        for (ProcessEdgeDTO edge : edges) {
            appendPart(builder, edge.getEdgeCode());
            appendPart(builder, edge.getSourceNodeCode());
            appendPart(builder, edge.getTargetNodeCode());
            appendPart(builder, edge.getConditionExpression());
            appendPart(builder, edge.getDefaultEdge());
            appendPart(builder, edge.getSortOrder());
        }
        appendPart(builder, Integer.valueOf(formFields.size()));
        for (ProcessFormFieldDTO formField : formFields) {
            appendPart(builder, formField.getFieldCode());
            appendPart(builder, formField.getFieldName());
            appendPart(builder, formField.getFieldType());
            appendPart(builder, formField.getControlType());
            appendPart(builder, formField.getRequired());
            appendPart(builder, formField.getValidationRule());
            appendPart(builder, formField.getDefaultValue());
            appendPart(builder, formField.getSortOrder());
        }
        appendPart(builder, Integer.valueOf(attachmentConfigs.size()));
        for (ProcessAttachmentConfigDTO attachmentConfig : attachmentConfigs) {
            appendPart(builder, attachmentConfig.getAttachmentConfigId());
            appendPart(builder, attachmentConfig.getAttachmentTemplateId());
            appendPart(builder, attachmentConfig.getAttachmentCode());
            appendPart(builder, attachmentConfig.getRequired());
            appendPart(builder, attachmentConfig.getMinCount());
            appendPart(builder, attachmentConfig.getMaxCount());
            appendPart(builder, attachmentConfig.getSortOrder());
            List<String> nodeCodes = attachmentConfig.getApplicableNodeCodes();
            appendPart(builder, Integer.valueOf(nodeCodes == null ? 0 : nodeCodes.size()));
            if (nodeCodes != null) {
                for (String nodeCode : nodeCodes) {
                    appendPart(builder, nodeCode);
                }
            }
        }
        return sha256(builder.toString());
    }

    private void appendPart(StringBuilder builder, Object value) {
        String text = value == null ? "" : String.valueOf(value);
        builder.append(text.length()).append(':').append(text).append('|');
    }

    /**
     * 注册图缓存失效通知。
     *
     * @param definitionId 已变更的流程定义 ID，用于精确失效定义级缓存
     */
    private void registerGraphCacheInvalidation(final String definitionId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    processDefinitionCache.invalidate(definitionId);
                }
            });
            return;
        }
        processDefinitionCache.invalidate(definitionId);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte item : bytes) {
                String hex = Integer.toHexString(item & 0xff);
                if (hex.length() == 1) {
                    builder.append('0');
                }
                builder.append(hex);
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    private OperationResult deleteOperationResult(String operationId, String definitionId, boolean replayed) {
        OperationResult result = new OperationResult();
        result.setOperationId(operationId);
        result.setTargetType(OperationTargetTypeEnum.DEFINITION);
        result.setTargetId(definitionId);
        result.setDeleted(true);
        result.setReplayed(replayed);
        return result;
    }

    private int totalPages(long total, int pageSize) {
        if (total <= 0L) {
            return 0;
        }
        return (int) ((total + pageSize - 1L) / pageSize);
    }

    private String newId() {
        return UUID.randomUUID().toString();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String trimToNull(String value) {
        if (!hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
