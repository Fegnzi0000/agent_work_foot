package com.hyf.agent_work_foot.common;

import jakarta.validation.ConstraintViolationException;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * REST 接口的统一异常出口。
 *
 * <p>将业务、Bean Validation、JSON 解析和路径参数异常转换为公共 ErrorResponse。</p>
 */
@RestControllerAdvice
public class ApiExceptionHandler {
    /** 作用：转换业务异常。输入：ApiException。输出：异常指定状态与公共错误体。逻辑：保留业务码和字段详情。 */
    @ExceptionHandler(ApiException.class)
    ResponseEntity<ErrorResponse> handleApi(ApiException exception) {
        return ResponseEntity.status(exception.status())
                .body(ErrorResponse.of(exception.code(), exception.getMessage(), exception.details()));
    }

    /**
     * 作用：转换请求体字段校验异常。
     * 输入：MethodArgumentNotValidException。输出：400 VALIDATION_FAILED。
     * 逻辑：收集全部字段错误并按字段路径排序，使响应稳定可测试。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> handleBodyValidation(MethodArgumentNotValidException exception) {
        List<FieldErrorDetail> details = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldErrorDetail(error.getField(), error.getDefaultMessage()))
                .toList();
        return validation(details);
    }

    /** 作用：转换方法级约束错误。输入：ConstraintViolationException。输出：字段级400响应。逻辑：收集全部违反项。 */
    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ErrorResponse> handleConstraintValidation(ConstraintViolationException exception) {
        List<FieldErrorDetail> details = exception.getConstraintViolations().stream()
                .map(error -> new FieldErrorDetail(error.getPropertyPath().toString(), error.getMessage()))
                .toList();
        return validation(details);
    }

    /** 作用：转换未知字段、JSON类型和格式错误。输入：不可读请求体异常。输出：400。逻辑：隐藏解析器内部信息。 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException exception) {
        return validation(List.of(new FieldErrorDetail("body", "请求体格式错误或包含未知字段")));
    }

    /** 作用：转换路径或查询参数类型错误。输入：类型转换异常。输出：400。逻辑：返回具体参数名。 */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
        return validation(List.of(new FieldErrorDetail(exception.getName(), "参数格式不正确")));
    }

    /** 作用：生成统一参数错误响应。输入：字段详情。输出：400 VALIDATION_FAILED。逻辑：保留校验发现顺序及数组下标顺序。 */
    private ResponseEntity<ErrorResponse> validation(List<FieldErrorDetail> details) {
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of("VALIDATION_FAILED", "请求参数不合法", details));
    }
}
