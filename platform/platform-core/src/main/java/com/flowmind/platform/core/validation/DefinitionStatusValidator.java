package com.flowmind.platform.core.validation;

import com.flowmind.platform.api.enums.DefinitionErrorCodes;
import com.flowmind.platform.core.definition.DefinitionStateException;

/**
 * 校验流程定义的冻结状态组合。
 *
 * <p>M0.5 只表达状态组合约束，不负责执行发布、激活、归档等动作。</p>
 *
 * @author Yuxin Xu
 * @since 2026-07-15
 */
public class DefinitionStatusValidator {

    public static final String DRAFT = "DRAFT";
    public static final String PUBLISHED = "PUBLISHED";
    public static final String ARCHIVED = "ARCHIVED";
    public static final String INACTIVE = "INACTIVE";
    public static final String ACTIVE = "ACTIVE";
    public static final String OFF = "OFF";
    public static final String ON = "ON";

    public void validate(String definitionStatus,
                         String activationStatus,
                         String grayStatus,
                         String grayRuleConfig) {
        requireKnown(definitionStatus, activationStatus, grayStatus);
        // 灰度开启必须携带规则，避免后续运行时无法判定命中范围。
        if (ON.equals(grayStatus) && isBlank(grayRuleConfig)) {
            throw new FrozenValidationException(
                    FrozenValidationErrorCodes.DEFINITION_GRAY_RULE_REQUIRED,
                    "Gray status ON requires gray rule config.");
        }
        // 草稿态只能保持未激活、未灰度，不允许对外承载流量。
        if (DRAFT.equals(definitionStatus)
                && (!INACTIVE.equals(activationStatus) || !OFF.equals(grayStatus))) {
            throw invalidCombination(definitionStatus, activationStatus, grayStatus);
        }
        if (PUBLISHED.equals(definitionStatus)) {
            return;
        }
        // 归档定义必须完全下线，避免被运行时继续选中。
        if (ARCHIVED.equals(definitionStatus)
                && (!INACTIVE.equals(activationStatus) || !OFF.equals(grayStatus))) {
            throw invalidCombination(definitionStatus, activationStatus, grayStatus);
        }
    }

    /**
     * 校验流程定义是否处于允许编辑的草稿态。
     *
     * @param definitionStatus 定义状态，典型值：DRAFT、PUBLISHED、ARCHIVED
     * @param activationStatus 激活状态，典型值：INACTIVE、ACTIVE
     * @param grayStatus       灰度状态，典型值：OFF、ON
     */
    public void validateEditable(String definitionStatus,
                                 String activationStatus,
                                 String grayStatus) {
        if (!DRAFT.equals(definitionStatus)
                || !INACTIVE.equals(activationStatus)
                || !OFF.equals(grayStatus)) {
            throw new DefinitionStateException(DefinitionErrorCodes.DEFINITION_NOT_EDITABLE,
                    "definition must be DRAFT + INACTIVE + OFF");
        }
    }

    private void requireKnown(String definitionStatus, String activationStatus, String grayStatus) {
        if (!DRAFT.equals(definitionStatus)
                && !PUBLISHED.equals(definitionStatus)
                && !ARCHIVED.equals(definitionStatus)) {
            throw invalidCombination(definitionStatus, activationStatus, grayStatus);
        }
        if (!INACTIVE.equals(activationStatus) && !ACTIVE.equals(activationStatus)) {
            throw invalidCombination(definitionStatus, activationStatus, grayStatus);
        }
        if (!OFF.equals(grayStatus) && !ON.equals(grayStatus)) {
            throw invalidCombination(definitionStatus, activationStatus, grayStatus);
        }
    }

    private FrozenValidationException invalidCombination(String definitionStatus,
                                                         String activationStatus,
                                                         String grayStatus) {
        return new FrozenValidationException(
                FrozenValidationErrorCodes.DEFINITION_STATUS_COMBINATION_INVALID,
                "Invalid definition status combination: definitionStatus="
                        + definitionStatus + ", activationStatus=" + activationStatus
                        + ", grayStatus=" + grayStatus + ".");
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
