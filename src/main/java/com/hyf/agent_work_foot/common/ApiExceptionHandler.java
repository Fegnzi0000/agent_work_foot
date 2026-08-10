package com.hyf.agent_work_foot.common;

import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(ApiException.class)
    ResponseEntity<ErrorBody> handleApi(ApiException exception) {
        return ResponseEntity.status(exception.status()).body(error(exception.code(), exception.getMessage()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class})
    ResponseEntity<ErrorBody> handleValidation(Exception exception) {
        return ResponseEntity.badRequest().body(error("VALIDATION_FAILED", "请求参数不合法"));
    }

    private ErrorBody error(String code, String message) {
        return new ErrorBody(code, message, List.of(), RequestIdContext.current(), Instant.now());
    }

    record ErrorBody(String code, String message, List<Object> details, String requestId, Instant timestamp) { }
}
