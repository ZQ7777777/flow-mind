package com.flowmind.platform.core.definition;

import com.flowmind.platform.api.dto.ProcessAttachmentTemplateDTO;
import com.flowmind.platform.api.dto.ProcessDefinitionDetailDTO;
import com.flowmind.platform.api.dto.ProcessEdgeDTO;
import com.flowmind.platform.api.dto.ProcessFormFieldDTO;
import com.flowmind.platform.api.dto.ProcessNodeDTO;
import com.flowmind.platform.api.dto.ValidationResult;
import com.flowmind.platform.api.enums.ActivationStatusEnum;
import com.flowmind.platform.api.enums.DefinitionStatusEnum;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 已发布且激活流程定义详情的进程内缓存。
 *
 * <p>缓存不加载定义、不调用 Service 或 Repository。定义写侧应在事务提交后调用
 * {@link #invalidate(String)}，事务回滚时不得调用该方法。</p>
 *
 * @author FlowMind
 * @since 2026-07-20
 */
@Component
public class ProcessDefinitionCache {

    /** 定义详情缓存条目，键为流程定义 ID。 */
    private final ConcurrentMap<String, ProcessDefinitionDetailDTO> entries =
            new ConcurrentHashMap<String, ProcessDefinitionDetailDTO>();
    /**
     * 定义 ID 对应的失效世代；其对象监视器同时串行化该定义的写入和失效，避免旧加载在失效后回写。
     */
    private final ConcurrentMap<String, AtomicLong> generations =
            new ConcurrentHashMap<String, AtomicLong>();

    /**
     * 读取指定流程定义详情。
     *
     * <p>定义 ID 已唯一定位数据库中的固定版本；运行时调用方仍应将返回详情的版本与实例快照核对。</p>
     *
     * @param definitionId 流程定义 ID
     * @return 深层防御性副本；未命中或参数非法时返回 {@code null}
     */
    public ProcessDefinitionDetailDTO get(String definitionId) {
        if (isBlank(definitionId)) {
            return null;
        }
        ProcessDefinitionDetailDTO cached = entries.get(definitionId);
        return cached == null ? null : copyOf(cached);
    }

    /**
     * 在加载流程定义详情前捕获当前失效世代。
     *
     * <p>读侧在缓存未命中后，应先调用本方法，再加载详情，并将返回令牌传给
     * {@link #put(ProcessDefinitionDetailDTO, ValidationResult, CacheGeneration)}。若加载期间定义
     * 已提交新内容并失效缓存，旧令牌对应的写入会被拒绝。</p>
     *
     * @param definitionId 流程定义 ID
     * @return 当前定义的缓存世代；参数非法时返回 {@code null}
     */
    public CacheGeneration captureGeneration(String definitionId) {
        if (isBlank(definitionId)) {
            return null;
        }
        return new CacheGeneration(definitionId, generationOf(definitionId).get());
    }

    /**
     * 写入一个已经通过发布校验的完整流程定义详情。
     *
     * <p>草稿、未发布、未激活、校验失败、加载结果为空或缺少键字段的详情均不会写入。</p>
     *
     * @param definition 流程定义详情
     * @param validationResult 发布校验结果
     * @param generation 加载前捕获的缓存世代
     * @return 实际写入缓存时返回 {@code true}
     */
    public boolean put(ProcessDefinitionDetailDTO definition,
                       ValidationResult validationResult,
                       CacheGeneration generation) {
        if (!isCacheable(definition, validationResult)
                || generation == null
                || !definition.getId().equals(generation.definitionId)) {
            return false;
        }
        ProcessDefinitionDetailDTO copied = copyOf(definition);
        AtomicLong currentGeneration = generationOf(definition.getId());
        synchronized (currentGeneration) {
            if (currentGeneration.get() != generation.value) {
                return false;
            }
            entries.put(definition.getId(), copied);
            return true;
        }
    }

    /**
     * 失效流程定义缓存。
     *
     * @param definitionId 流程定义 ID
     */
    public void invalidate(String definitionId) {
        if (isBlank(definitionId)) {
            return;
        }
        AtomicLong currentGeneration = generationOf(definitionId);
        synchronized (currentGeneration) {
            currentGeneration.incrementAndGet();
            entries.remove(definitionId);
        }
    }

    private boolean isCacheable(ProcessDefinitionDetailDTO definition,
                                ValidationResult validationResult) {
        return definition != null
                && validationResult != null
                && validationResult.isValid()
                && !isBlank(definition.getId())
                && definition.getVersion() != null
                && DefinitionStatusEnum.PUBLISHED.equals(definition.getDefinitionStatus())
                && ActivationStatusEnum.ACTIVE.equals(definition.getActivationStatus());
    }

    private AtomicLong generationOf(String definitionId) {
        AtomicLong existing = generations.get(definitionId);
        if (existing != null) {
            return existing;
        }
        AtomicLong created = new AtomicLong();
        AtomicLong raced = generations.putIfAbsent(definitionId, created);
        return raced == null ? created : raced;
    }

    private static ProcessDefinitionDetailDTO copyOf(ProcessDefinitionDetailDTO source) {
        ProcessDefinitionDetailDTO target = new ProcessDefinitionDetailDTO();
        target.setId(source.getId());
        target.setProcessCode(source.getProcessCode());
        target.setProcessName(source.getProcessName());
        target.setSystemCode(source.getSystemCode());
        target.setVersion(source.getVersion());
        target.setDefinitionStatus(source.getDefinitionStatus());
        target.setActivationStatus(source.getActivationStatus());
        target.setGrayStatus(source.getGrayStatus());
        target.setGrayRuleConfig(source.getGrayRuleConfig());
        target.setCreatedBy(source.getCreatedBy());
        target.setCreatedAt(source.getCreatedAt());
        target.setUpdatedBy(source.getUpdatedBy());
        target.setUpdatedAt(source.getUpdatedAt());
        target.setNodes(copyNodes(source.getNodes()));
        target.setEdges(copyEdges(source.getEdges()));
        target.setFormFields(copyFormFields(source.getFormFields()));
        target.setAttachmentTemplates(copyAttachmentTemplates(source.getAttachmentTemplates()));
        return target;
    }

    private static List<ProcessNodeDTO> copyNodes(List<ProcessNodeDTO> source) {
        if (source == null) {
            return null;
        }
        List<ProcessNodeDTO> target = new ArrayList<ProcessNodeDTO>(source.size());
        for (ProcessNodeDTO node : source) {
            target.add(node == null ? null : copyNode(node));
        }
        return target;
    }

    private static ProcessNodeDTO copyNode(ProcessNodeDTO source) {
        ProcessNodeDTO target = new ProcessNodeDTO();
        target.setId(source.getId());
        target.setDefinitionId(source.getDefinitionId());
        target.setNodeCode(source.getNodeCode());
        target.setNodeName(source.getNodeName());
        target.setNodeType(source.getNodeType());
        target.setPairedGatewayCode(source.getPairedGatewayCode());
        target.setApproverRuleType(source.getApproverRuleType());
        target.setApproverRuleConfig(source.getApproverRuleConfig());
        target.setMultiInstanceMode(source.getMultiInstanceMode());
        target.setListenerConfig(source.getListenerConfig());
        target.setTimeoutConfig(source.getTimeoutConfig());
        target.setReminderConfig(source.getReminderConfig());
        target.setNoticeConfig(source.getNoticeConfig());
        target.setPositionX(source.getPositionX());
        target.setPositionY(source.getPositionY());
        target.setSortOrder(source.getSortOrder());
        return target;
    }

    private static List<ProcessEdgeDTO> copyEdges(List<ProcessEdgeDTO> source) {
        if (source == null) {
            return null;
        }
        List<ProcessEdgeDTO> target = new ArrayList<ProcessEdgeDTO>(source.size());
        for (ProcessEdgeDTO edge : source) {
            target.add(edge == null ? null : copyEdge(edge));
        }
        return target;
    }

    private static ProcessEdgeDTO copyEdge(ProcessEdgeDTO source) {
        ProcessEdgeDTO target = new ProcessEdgeDTO();
        target.setId(source.getId());
        target.setDefinitionId(source.getDefinitionId());
        target.setEdgeCode(source.getEdgeCode());
        target.setSourceNodeCode(source.getSourceNodeCode());
        target.setTargetNodeCode(source.getTargetNodeCode());
        target.setConditionExpression(source.getConditionExpression());
        target.setDefaultEdge(source.getDefaultEdge());
        target.setSortOrder(source.getSortOrder());
        return target;
    }

    private static List<ProcessFormFieldDTO> copyFormFields(List<ProcessFormFieldDTO> source) {
        if (source == null) {
            return null;
        }
        List<ProcessFormFieldDTO> target = new ArrayList<ProcessFormFieldDTO>(source.size());
        for (ProcessFormFieldDTO formField : source) {
            target.add(formField == null ? null : copyFormField(formField));
        }
        return target;
    }

    private static ProcessFormFieldDTO copyFormField(ProcessFormFieldDTO source) {
        ProcessFormFieldDTO target = new ProcessFormFieldDTO();
        target.setId(source.getId());
        target.setDefinitionId(source.getDefinitionId());
        target.setFieldCode(source.getFieldCode());
        target.setFieldName(source.getFieldName());
        target.setFieldType(source.getFieldType());
        target.setControlType(source.getControlType());
        target.setRequired(source.getRequired());
        target.setValidationRule(source.getValidationRule());
        target.setDefaultValue(source.getDefaultValue());
        target.setSortOrder(source.getSortOrder());
        return target;
    }

    private static List<ProcessAttachmentTemplateDTO> copyAttachmentTemplates(
            List<ProcessAttachmentTemplateDTO> source) {
        if (source == null) {
            return null;
        }
        List<ProcessAttachmentTemplateDTO> target =
                new ArrayList<ProcessAttachmentTemplateDTO>(source.size());
        for (ProcessAttachmentTemplateDTO attachmentTemplate : source) {
            target.add(attachmentTemplate == null ? null : copyAttachmentTemplate(attachmentTemplate));
        }
        return target;
    }

    private static ProcessAttachmentTemplateDTO copyAttachmentTemplate(
            ProcessAttachmentTemplateDTO source) {
        ProcessAttachmentTemplateDTO target = new ProcessAttachmentTemplateDTO();
        target.setId(source.getId());
        target.setAttachmentConfigId(source.getAttachmentConfigId());
        target.setDefinitionId(source.getDefinitionId());
        target.setConfigStatus(source.getConfigStatus());
        target.setActivatedAt(source.getActivatedAt());
        target.setAttachmentTemplateId(source.getAttachmentTemplateId());
        target.setAttachmentCode(source.getAttachmentCode());
        target.setTemplateVersion(source.getTemplateVersion());
        target.setAttachmentName(source.getAttachmentName());
        target.setDescription(source.getDescription());
        target.setAllowedExtensions(copyStrings(source.getAllowedExtensions()));
        target.setMaxSizeBytes(source.getMaxSizeBytes());
        target.setTemplateStatus(source.getTemplateStatus());
        target.setRequired(source.getRequired());
        target.setMinCount(source.getMinCount());
        target.setMaxCount(source.getMaxCount());
        target.setApplicableNodeCodes(copyStrings(source.getApplicableNodeCodes()));
        target.setSortOrder(source.getSortOrder());
        target.setCreatedBy(source.getCreatedBy());
        target.setCreatedAt(source.getCreatedAt());
        target.setUpdatedBy(source.getUpdatedBy());
        target.setUpdatedAt(source.getUpdatedAt());
        return target;
    }

    private static List<String> copyStrings(List<String> source) {
        return source == null ? null : new ArrayList<String>(source);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    /**
     * 定义详情加载时的缓存世代令牌。
     *
     * <p>令牌不携带定义内容，只用于阻止失效前的在途加载在失效后重新写入旧快照。</p>
     */
    public static final class CacheGeneration {
        /** 令牌所属流程定义 ID。 */
        private final String definitionId;
        /** 加载开始时观察到的失效世代。 */
        private final long value;

        private CacheGeneration(String definitionId, long value) {
            this.definitionId = definitionId;
            this.value = value;
        }
    }

}
