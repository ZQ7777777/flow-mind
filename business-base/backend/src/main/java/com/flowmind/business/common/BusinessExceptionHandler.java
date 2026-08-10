package com.flowmind.business.common;

import com.flowmind.business.security.BusinessAccessDeniedException;
import com.flowmind.business.security.BusinessAuthenticationException;
import com.flowmind.platform.api.error.PlatformError;
import com.flowmind.platform.api.error.PlatformErrorCategory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import javax.servlet.http.HttpServletRequest;
import javax.validation.ConstraintViolationException;
import java.util.Collections;

/**
 * 业务后端统一异常到 HTTP 错误响应的适配器。
 *
 * @author FlowMind
 * @since 2026-08-10
 */
@RestControllerAdvice
public class BusinessExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(BusinessExceptionHandler.class);

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class,
            ConstraintViolationException.class, HttpMessageNotReadableException.class,
            MissingRequestHeaderException.class, MissingServletRequestParameterException.class,
            MissingServletRequestPartException.class, MethodArgumentTypeMismatchException.class,
            HttpMediaTypeNotSupportedException.class, MaxUploadSizeExceededException.class})
    public ResponseEntity<ApiErrorResponse> handleInvalidRequest(Exception exception,
                                                                 HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "BUSINESS_REQUEST_INVALID", "请求参数不合法", request);
    }

    @ExceptionHandler(BusinessAuthenticationException.class)
    public ResponseEntity<ApiErrorResponse> handleAuthentication(BusinessAuthenticationException exception,
                                                                 HttpServletRequest request) {
        return error(HttpStatus.UNAUTHORIZED, "BUSINESS_AUTHENTICATION_REQUIRED", exception.getMessage(), request);
    }

    @ExceptionHandler(BusinessAccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(BusinessAccessDeniedException exception,
                                                               HttpServletRequest request) {
        return error(HttpStatus.FORBIDDEN, "BUSINESS_ACCESS_DENIED", exception.getMessage(), request);
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiErrorResponse> handleDataAccess(DataAccessException exception,
                                                             HttpServletRequest request) {
        if (isSqliteBusy(exception)) {
            return error(HttpStatus.SERVICE_UNAVAILABLE, "FLOW_DATABASE_BUSY", "数据库繁忙，请稍后重试", request);
        }
        LOGGER.error("Database operation failed, requestId={}", requestId(request), exception);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "FLOW_DATABASE_ERROR", "数据库操作失败", request);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiErrorResponse> handleRuntime(RuntimeException exception,
                                                          HttpServletRequest request) {
        if (exception instanceof PlatformError) {
            PlatformError platformError = (PlatformError) exception;
            HttpStatus status = status(platformError.getErrorCategory());
            return error(status, platformError.getErrorCode(), safeMessage(exception.getMessage(), status), request);
        }
        if (exception instanceof IllegalArgumentException) {
            return error(HttpStatus.BAD_REQUEST, "BUSINESS_REQUEST_INVALID",
                    safeMessage(exception.getMessage(), HttpStatus.BAD_REQUEST), request);
        }
        LOGGER.error("Unhandled business exception, requestId={}", requestId(request), exception);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "BUSINESS_INTERNAL_ERROR", "系统内部错误", request);
    }

    private ResponseEntity<ApiErrorResponse> error(HttpStatus status,
                                                   String code,
                                                   String message,
                                                   HttpServletRequest request) {
        ApiErrorResponse body = new ApiErrorResponse();
        body.setStatus(status.value());
        body.setCode(code);
        body.setMessage(message);
        body.setRequestId(requestId(request));
        body.setDetails(Collections.<String, Object>emptyMap());
        return new ResponseEntity<ApiErrorResponse>(body, status);
    }

    private HttpStatus status(PlatformErrorCategory category) {
        if (category == PlatformErrorCategory.UNAUTHENTICATED) {
            return HttpStatus.UNAUTHORIZED;
        }
        if (category == PlatformErrorCategory.FORBIDDEN) {
            return HttpStatus.FORBIDDEN;
        }
        if (category == PlatformErrorCategory.NOT_FOUND) {
            return HttpStatus.NOT_FOUND;
        }
        if (category == PlatformErrorCategory.CONFLICT) {
            return HttpStatus.CONFLICT;
        }
        if (category == PlatformErrorCategory.DEPENDENCY_FAILURE) {
            return HttpStatus.BAD_GATEWAY;
        }
        if (category == PlatformErrorCategory.TEMPORARILY_UNAVAILABLE) {
            return HttpStatus.SERVICE_UNAVAILABLE;
        }
        if (category == PlatformErrorCategory.INTERNAL) {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
        return HttpStatus.BAD_REQUEST;
    }

    private String requestId(HttpServletRequest request) {
        Object value = request.getAttribute(RequestIdFilter.REQUEST_ATTRIBUTE);
        return value == null ? null : String.valueOf(value);
    }

    private String safeMessage(String message, HttpStatus status) {
        if (message != null && !message.trim().isEmpty() && !status.is5xxServerError()) {
            return message;
        }
        return status.is5xxServerError() ? "系统服务暂时不可用" : "请求处理失败";
    }

    private boolean isSqliteBusy(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && (message.contains("SQLITE_BUSY") || message.contains("database is locked"))) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
