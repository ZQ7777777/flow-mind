package com.flowmind.platform.core.validation;

import com.flowmind.platform.api.dto.ProcessDefinitionQuery;
import com.flowmind.platform.api.request.CopyProcessDefinitionRequest;
import com.flowmind.platform.api.request.CreateProcessDefinitionRequest;
import com.flowmind.platform.api.request.DefinitionOperationRequest;
import com.flowmind.platform.api.request.OperationRequest;
import com.flowmind.platform.api.request.SaveProcessGraphRequest;
import com.flowmind.platform.api.enums.DefinitionErrorCodes;
import com.flowmind.platform.core.definition.DefinitionValidationException;
import com.flowmind.platform.core.query.PageQueryNormalizer;

import java.util.List;

/**
 * 流程定义管理基础请求校验工具。
 *
 * @author Yuxin Xu
 * @since 2026-07-17
 */
public final class DefinitionRequestValidator {

    public static final int DEFAULT_PAGE_NO = PageQueryNormalizer.DEFAULT_PAGE_NO;
    public static final int DEFAULT_PAGE_SIZE = PageQueryNormalizer.DEFAULT_PAGE_SIZE;
    public static final int MAX_PAGE_SIZE = PageQueryNormalizer.MAX_PAGE_SIZE;
    public static final int MAX_PROCESS_CODE_LENGTH = 64;
    public static final int MAX_PROCESS_NAME_LENGTH = 128;
    public static final int MAX_SYSTEM_CODE_LENGTH = 64;

    private DefinitionRequestValidator() {
    }

    /**
     * 校验创建流程定义请求，主要校验请求是否为空、长度是否超过限制
     * @param request
     */
    public static void validateCreate(CreateProcessDefinitionRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        validateOperationId(request);
        validateOperatorUserId(request.getOperatorUserId());
        validateRequiredLength(request.getProcessCode(), "processCode", MAX_PROCESS_CODE_LENGTH);
        validateRequiredLength(request.getProcessName(), "processName", MAX_PROCESS_NAME_LENGTH);
        validateRequiredLength(request.getSystemCode(), "systemCode", MAX_SYSTEM_CODE_LENGTH);
    }

    /**
     * 校验复制流程定义请求。
     * @param request
     */
    public static void validateCopy(CopyProcessDefinitionRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        validateOperationId(request);
        validateOperatorUserId(request.getOperatorUserId());
        validateOptionalLength(request.getProcessCode(), "processCode", MAX_PROCESS_CODE_LENGTH);
        validateOptionalLength(request.getProcessName(), "processName", MAX_PROCESS_NAME_LENGTH);
        validateOptionalLength(request.getSystemCode(), "systemCode", MAX_SYSTEM_CODE_LENGTH);
    }

    /**
     * 用于校验针对某个流程定义的操作请求。（发布、激活、停用、删除、查询、归档）
     * @param request
     */
    public static void validateDefinitionOperation(DefinitionOperationRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        validateOperationId(request);
        validateOperatorUserId(request.getOperatorUserId());
        validateRequiredLength(request.getDefinitionId(), "definitionId", MAX_PROCESS_CODE_LENGTH);
    }

    /**
     * 用于校验保存流程图请求
     * @param request
     */
    public static void validateSaveGraph(SaveProcessGraphRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        validateOperationId(request);
        validateOperatorUserId(request.getOperatorUserId());
        validateNotEmpty(request.getNodes(), "nodes");
        validateNotEmpty(request.getEdges(), "edges");
    }

    /**
     * 不仅校验，还会返回一个规范化后的查询对象
     * @param query
     * @return
     */
    public static ProcessDefinitionQuery normalizeQuery(ProcessDefinitionQuery query) {
        ProcessDefinitionQuery normalized = query == null ? new ProcessDefinitionQuery() : query;
        PageQueryNormalizer.normalize(normalized);
        validateOptionalLength(normalized.getProcessCode(), "processCode", MAX_PROCESS_CODE_LENGTH);
        validateOptionalLength(normalized.getProcessName(), "processName", MAX_PROCESS_NAME_LENGTH);
        validateOptionalLength(normalized.getSystemCode(), "systemCode", MAX_SYSTEM_CODE_LENGTH);
        return normalized;
    }

    /**
     * 用于校验流程平台状态修改请求
     * @param request
     */
    private static void validateOperationId(OperationRequest request) {
        if (!hasText(request.getOperationId())) {
            throw new DefinitionValidationException(DefinitionErrorCodes.OPERATION_ID_REQUIRED,
                    "operationId must not be empty");
        }
        validateLength(request.getOperationId(), "operationId", MAX_PROCESS_CODE_LENGTH);
    }

    /**
     * 校验操作人的id长度
     * @param operatorUserId
     */
    private static void validateOperatorUserId(String operatorUserId) {
        validateRequiredLength(operatorUserId, "operatorUserId", MAX_PROCESS_CODE_LENGTH);
    }

    /**
     * 校验文件不能为空并且不能超过最大长度
     * @param value
     * @param fieldName
     * @param maxLength
     */
    private static void validateRequiredLength(String value, String fieldName, int maxLength) {
        if (!hasText(value)) {
            throw new DefinitionValidationException(DefinitionErrorCodes.DEFINITION_INVALID,
                    fieldName + " must not be empty");
        }
        validateLength(value, fieldName, maxLength);
    }

    /**
     * 校验value非空并校验长度
     * @param value
     * @param fieldName
     * @param maxLength
     */
    private static void validateOptionalLength(String value, String fieldName, int maxLength) {
        if (value == null || value.trim().isEmpty()) {
            return;
        }
        validateLength(value, fieldName, maxLength);
    }

    /**
     * 用于校验value必须小于最大长度限制
     * @param value
     * @param fieldName
     * @param maxLength
     */
    private static void validateLength(String value, String fieldName, int maxLength) {
        if (value.length() > maxLength) {
            throw new DefinitionValidationException(DefinitionErrorCodes.DEFINITION_INVALID,
                    fieldName + " length must be <= " + maxLength);
        }
    }

    /**
     * 用于校验列表非空
     * @param values
     * @param fieldName
     */
    private static void validateNotEmpty(List<?> values, String fieldName) {
        if (values == null || values.isEmpty()) {
            throw new DefinitionValidationException(DefinitionErrorCodes.DEFINITION_INVALID,
                    fieldName + " must not be empty");
        }
    }

    /**
     * 判断是否为空
     * @param value
     * @return
     */
    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
