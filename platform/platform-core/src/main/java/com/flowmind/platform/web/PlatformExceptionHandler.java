package com.flowmind.platform.web;

import com.flowmind.platform.api.dto.ApiErrorDTO;
import com.flowmind.platform.core.definition.DefinitionStateException;
import com.flowmind.platform.core.definition.DefinitionValidationException;
import com.flowmind.platform.core.runtime.RuntimeConfigurationException;
import com.flowmind.platform.core.runtime.RuntimeStateException;
import com.flowmind.platform.core.runtime.RuntimeValidationException;
import com.flowmind.platform.core.validation.FrozenValidationException;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 平台 REST 异常适配器。
 *
 * @author FlowMind
 * @since 2026-07-23
 */
@RestControllerAdvice
public class PlatformExceptionHandler {

    /**
     * 处理流程定义请求校验异常。
     *
     * @param exception 流程定义校验异常
     * @return HTTP 400 错误响应
     */
    @ExceptionHandler(DefinitionValidationException.class)
    public ResponseEntity<ApiErrorDTO> handleDefinitionValidation(DefinitionValidationException exception) {
        return error(HttpStatus.BAD_REQUEST, exception.getErrorCode(), exception.getMessage());
    }

    /**
     * 处理流程定义状态异常。
     *
     * @param exception 流程定义状态异常
     * @return HTTP 409 错误响应
     */
    @ExceptionHandler(DefinitionStateException.class)
    public ResponseEntity<ApiErrorDTO> handleDefinitionState(DefinitionStateException exception) {
        return error(HttpStatus.CONFLICT, exception.getErrorCode(), exception.getMessage());
    }

    /**
     * 处理冻结规则校验异常。
     *
     * @param exception 冻结规则校验异常
     * @return HTTP 400 错误响应
     */
    @ExceptionHandler(FrozenValidationException.class)
    public ResponseEntity<ApiErrorDTO> handleFrozenValidation(FrozenValidationException exception) {
        return error(HttpStatus.BAD_REQUEST, exception.getErrorCode(), exception.getMessage());
    }

    /**
     * 处理运行期请求校验异常。
     *
     * @param exception 运行期请求校验异常
     * @return HTTP 400 错误响应
     */
    @ExceptionHandler(RuntimeValidationException.class)
    public ResponseEntity<ApiErrorDTO> handleRuntimeValidation(RuntimeValidationException exception) {
        return error(HttpStatus.BAD_REQUEST, exception.getErrorCode(), exception.getMessage());
    }

    /**
     * 处理运行期配置异常。
     *
     * @param exception 运行期配置异常
     * @return HTTP 400 错误响应
     */
    @ExceptionHandler(RuntimeConfigurationException.class)
    public ResponseEntity<ApiErrorDTO> handleRuntimeConfiguration(RuntimeConfigurationException exception) {
        return error(HttpStatus.BAD_REQUEST, exception.getErrorCode(), exception.getMessage());
    }

    /**
     * 处理运行期状态异常。
     *
     * @param exception 运行期状态异常
     * @return HTTP 409 错误响应
     */
    @ExceptionHandler(RuntimeStateException.class)
    public ResponseEntity<ApiErrorDTO> handleRuntimeState(RuntimeStateException exception) {
        return error(HttpStatus.CONFLICT, exception.getErrorCode(), exception.getMessage());
    }

    /**
     * 处理通用请求参数异常。
     *
     * @param exception 请求参数异常
     * @return HTTP 400 错误响应
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorDTO> handleIllegalArgument(IllegalArgumentException exception) {
        return error(HttpStatus.BAD_REQUEST, "FLOW_REQUEST_INVALID", exception.getMessage());
    }

    /**
     * 处理通用状态冲突异常。
     *
     * @param exception 状态冲突异常
     * @return HTTP 409 错误响应
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiErrorDTO> handleIllegalState(IllegalStateException exception) {
        return error(HttpStatus.CONFLICT, "FLOW_STATE_INVALID", exception.getMessage());
    }

    /**
     * 处理数据库访问异常。
     *
     * @param exception 数据库访问异常
     * @return HTTP 错误响应
     */
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiErrorDTO> handleDataAccess(DataAccessException exception) {
        if (isSqliteBusy(exception)) {
            return error(HttpStatus.SERVICE_UNAVAILABLE, "FLOW_DATABASE_BUSY",
                    "Database is busy. Please retry later.");
        }
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "FLOW_DATABASE_ERROR", "Database operation failed.");
    }

    private ResponseEntity<ApiErrorDTO> error(HttpStatus status, String code, String message) {
        ApiErrorDTO error = new ApiErrorDTO();
        error.setStatus(Integer.valueOf(status.value()));
        error.setCode(code);
        error.setMessage(message);
        return new ResponseEntity<ApiErrorDTO>(error, status);
    }

    private boolean isSqliteBusy(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            String message = current.getMessage();
            if (message != null
                    && (message.contains("SQLITE_BUSY") || message.contains("database is locked"))) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
