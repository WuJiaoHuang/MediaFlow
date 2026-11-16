package com.mediaflow.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {
  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  private ResponseEntity<ApiResponse<Void>> error(HttpStatus s, String m) {
    return ResponseEntity.status(s).body(ApiResponse.error(s.value() * 100, m));
  }

  @ExceptionHandler(BusinessException.class)
  ResponseEntity<ApiResponse<Void>> business(BusinessException e) {
    return error(e.status, e.getMessage());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ApiResponse<Void>> validation(MethodArgumentNotValidException e) {
    return error(
        HttpStatus.BAD_REQUEST,
        e.getBindingResult().getFieldErrors().stream()
            .map(f -> f.getField() + ": " + f.getDefaultMessage())
            .findFirst()
            .orElse("参数不合法"));
  }

  @ExceptionHandler({
    HttpMessageNotReadableException.class,
    HandlerMethodValidationException.class,
    MethodArgumentTypeMismatchException.class
  })
  ResponseEntity<ApiResponse<Void>> invalid(Exception e) {
    return error(HttpStatus.BAD_REQUEST, "请求参数不合法");
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<ApiResponse<Void>> conflict(Exception e) {
    return error(HttpStatus.CONFLICT, "数据冲突，请检查用户名或关联数据");
  }

  @ExceptionHandler(AccessDeniedException.class)
  ResponseEntity<ApiResponse<Void>> denied(Exception e) {
    return error(HttpStatus.FORBIDDEN, "权限不足");
  }

  @ExceptionHandler(NoResourceFoundException.class)
  ResponseEntity<ApiResponse<Void>> missing(Exception e) {
    return error(HttpStatus.NOT_FOUND, "资源不存在");
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ApiResponse<Void>> unexpected(Exception e) {
    if (e instanceof ErrorResponse frameworkError) {
      var status = frameworkError.getStatusCode();
      String message =
          switch (status.value()) {
            case 400 -> "请求参数不合法";
            case 404 -> "资源不存在";
            case 405 -> "不支持的请求方法";
            case 406 -> "不支持的响应格式";
            case 415 -> "不支持的请求内容类型";
            default -> status.is5xxServerError() ? "服务器内部错误" : "请求无法处理";
          };
      return ResponseEntity.status(status)
          .headers(frameworkError.getHeaders())
          .contentType(MediaType.APPLICATION_JSON)
          .body(ApiResponse.error(status.value() * 100, message));
    }
    log.error("Unhandled server error: {}", e.getClass().getName());
    return error(HttpStatus.INTERNAL_SERVER_ERROR, "服务器内部错误");
  }
}
