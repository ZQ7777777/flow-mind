package com.flowmind.platform.core.runtime;

import com.flowmind.platform.api.dto.ProcessDefinitionDetailDTO;
import com.flowmind.platform.api.dto.ValidationResult;
import com.flowmind.platform.api.enums.ActivationStatusEnum;
import com.flowmind.platform.api.enums.DefinitionStatusEnum;
import com.flowmind.platform.api.enums.GrayStatusEnum;
import com.flowmind.platform.api.service.ProcessDefinitionService;
import com.flowmind.platform.core.definition.ProcessDefinitionCache;
import com.flowmind.platform.core.validation.DefinitionModelValidator;
import com.flowmind.platform.persistence.entity.ProcessDefinitionEntity;
import com.flowmind.platform.persistence.entity.ProcessInstanceEntity;
import com.flowmind.platform.persistence.repository.ProcessDefinitionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 运行时流程定义读侧。
 *
 * <p>新实例先按流程编码选出可启动版本，再以定义 ID 进入缓存；已有实例仅按其冻结的定义 ID
 * 加载，流程编码和版本号只用于验证实例快照，绝不参与重新选版。</p>
 *
 * @author FlowMind
 * @since 2026-07-22
 */
@Component
public class RuntimeDefinitionLoader {

    /** 定义元数据仓储，仅用于新实例选择可启动版本。 */
    private final ProcessDefinitionRepository definitionRepository;
    /** 完整定义读取服务。 */
    private final ProcessDefinitionService definitionService;
    /** 已发布且激活定义的只读缓存。 */
    private final ProcessDefinitionCache definitionCache;
    /** 定义结构校验器。 */
    private final DefinitionModelValidator modelValidator;

    @Autowired
    public RuntimeDefinitionLoader(ProcessDefinitionRepository definitionRepository,
                                   ProcessDefinitionService definitionService,
                                   ProcessDefinitionCache definitionCache) {
        this(definitionRepository, definitionService, definitionCache, new DefinitionModelValidator());
    }

    RuntimeDefinitionLoader(ProcessDefinitionRepository definitionRepository,
                            ProcessDefinitionService definitionService,
                            ProcessDefinitionCache definitionCache,
                            DefinitionModelValidator modelValidator) {
        this.definitionRepository = definitionRepository;
        this.definitionService = definitionService;
        this.definitionCache = definitionCache;
        this.modelValidator = modelValidator;
    }

    /**
     * 为新实例选择并加载唯一的已发布、已激活、非灰度定义。
     *
     * @param processCode 流程业务编码
     * @return 可用于启动的完整流程定义
     */
    public ProcessDefinitionDetailDTO loadForStart(String processCode) {
        if (isBlank(processCode)) {
            throw new RuntimeValidationException(RuntimeErrorCodes.INVALID_ACTION,
                    "processCode is required to start a process");
        }
        ProcessDefinitionEntity selected = definitionRepository.findActiveFullByProcessCode(processCode);
        if (selected == null) {
            throw new RuntimeStateException(RuntimeErrorCodes.DEFINITION_NOT_ACTIVE,
                    "No active full-release definition exists for processCode: " + processCode);
        }
        ProcessDefinitionDetailDTO definition = loadByDefinitionId(selected.getId(), true);
        if (!equalsText(processCode, definition.getProcessCode())
                || !equalsValue(selected.getVersion(), definition.getVersion())) {
            throw new RuntimeStateException(RuntimeErrorCodes.DEFINITION_INVALID,
                    "selected definition metadata does not match loaded definition detail");
        }
        return definition;
    }

    /**
     * 按实例冻结的定义 ID 加载定义并验证版本、流程编码快照。
     *
     * @param instance 已持久化的流程实例
     * @return 与实例快照一致的完整流程定义
     */
    public ProcessDefinitionDetailDTO loadForInstance(ProcessInstanceEntity instance) {
        if (instance == null || isBlank(instance.getDefinitionId())) {
            throw new RuntimeValidationException(RuntimeErrorCodes.DEFINITION_INVALID,
                    "instance definitionId is required");
        }
        ProcessDefinitionDetailDTO definition = loadByDefinitionId(instance.getDefinitionId(), false);
        if (!equalsText(instance.getProcessCode(), definition.getProcessCode())
                || !equalsValue(instance.getVersion(), definition.getVersion())) {
            throw new RuntimeStateException(RuntimeErrorCodes.DEFINITION_INVALID,
                    "instance definition snapshot does not match definitionId: " + instance.getDefinitionId());
        }
        return definition;
    }

    /**
     * 以 definitionId 查询缓存或完整定义，并在允许时回填缓存。
     *
     * @param definitionId    固定流程定义 ID
     * @param requireStartable 是否要求定义仍满足新实例启动条件
     * @return 完整流程定义
     */
    private ProcessDefinitionDetailDTO loadByDefinitionId(String definitionId, boolean requireStartable) {
        ProcessDefinitionDetailDTO cached = definitionCache.get(definitionId);
        if (cached != null) {
            assertStartableWhenRequired(cached, requireStartable);
            return cached;
        }

        ProcessDefinitionCache.CacheGeneration generation = definitionCache.captureGeneration(definitionId);
        ProcessDefinitionDetailDTO definition = definitionService.getDefinition(definitionId);
        if (definition == null) {
            throw new RuntimeStateException(RuntimeErrorCodes.DEFINITION_NOT_FOUND,
                    "definition does not exist: " + definitionId);
        }
        if (!definitionId.equals(definition.getId())) {
            throw new RuntimeStateException(RuntimeErrorCodes.DEFINITION_INVALID,
                    "loaded definitionId does not match requested definitionId");
        }
        ValidationResult validation = modelValidator.validate(definition);
        if (!validation.isValid()) {
            throw new RuntimeStateException(RuntimeErrorCodes.DEFINITION_INVALID,
                    firstValidationMessage(validation));
        }
        assertStartableWhenRequired(definition, requireStartable);
        definitionCache.put(definition, validation, generation);
        return definition;
    }

    /** 新实例只接受已发布、已激活且未开启灰度的定义。 */
    private void assertStartableWhenRequired(ProcessDefinitionDetailDTO definition, boolean requireStartable) {
        if (requireStartable
                && (!DefinitionStatusEnum.PUBLISHED.equals(definition.getDefinitionStatus())
                || !ActivationStatusEnum.ACTIVE.equals(definition.getActivationStatus())
                || !GrayStatusEnum.OFF.equals(definition.getGrayStatus()))) {
            throw new RuntimeStateException(RuntimeErrorCodes.DEFINITION_NOT_ACTIVE,
                    "definition is not active for a new process instance");
        }
    }

    /** 返回首条模型问题，避免将内部校验结构泄漏到运行时调用方。 */
    private String firstValidationMessage(ValidationResult validation) {
        if (validation.getIssues() != null && !validation.getIssues().isEmpty()
                && !isBlank(validation.getIssues().get(0).getMessage())) {
            return validation.getIssues().get(0).getMessage();
        }
        return "definition model is invalid";
    }

    private static boolean equalsText(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }

    private static boolean equalsValue(Object left, Object right) {
        return left == null ? right == null : left.equals(right);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
