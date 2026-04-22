package com.example.be_a.global.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApiException(ApiException exception, HttpServletRequest request) {
        ErrorCode errorCode = exception.getErrorCode();
        return ResponseEntity.status(errorCode.getStatus())
            .body(ErrorResponse.of(errorCode, request.getRequestURI()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(
        MethodArgumentNotValidException exception,
        HttpServletRequest request
    ) {
        List<FieldErrorDetail> fieldErrors = exception.getBindingResult().getFieldErrors().stream()
            .map(this::toFieldErrorDetail)
            .toList();

        return ResponseEntity.badRequest()
            .body(ErrorResponse.of(ErrorCode.VALIDATION_ERROR, request.getRequestURI(), fieldErrors));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(
        ConstraintViolationException exception,
        HttpServletRequest request
    ) {
        List<FieldErrorDetail> fieldErrors = exception.getConstraintViolations().stream()
            .map(violation -> new FieldErrorDetail(violation.getPropertyPath().toString(), violation.getMessage()))
            .toList();

        return ResponseEntity.badRequest()
            .body(ErrorResponse.of(ErrorCode.VALIDATION_ERROR, request.getRequestURI(), fieldErrors));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(
        MethodArgumentTypeMismatchException exception,
        HttpServletRequest request
    ) {
        FieldErrorDetail fieldError = new FieldErrorDetail(exception.getName(), "허용되지 않은 형식입니다.");
        return ResponseEntity.badRequest()
            .body(ErrorResponse.of(ErrorCode.VALIDATION_ERROR, request.getRequestURI(), List.of(fieldError)));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleNotReadable(
        HttpMessageNotReadableException exception,
        HttpServletRequest request
    ) {
        return ResponseEntity.badRequest()
            .body(ErrorResponse.of(ErrorCode.VALIDATION_ERROR, request.getRequestURI()));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> handleMissingHeader(
        MissingRequestHeaderException exception,
        HttpServletRequest request
    ) {
        ErrorCode errorCode = "X-User-Id".equalsIgnoreCase(exception.getHeaderName())
            ? ErrorCode.MISSING_USER_ID
            : ErrorCode.VALIDATION_ERROR;

        return ResponseEntity.status(errorCode.getStatus())
            .body(ErrorResponse.of(errorCode, request.getRequestURI()));
    }

    private FieldErrorDetail toFieldErrorDetail(FieldError fieldError) {
        String reason = fieldError.getDefaultMessage() == null ? "유효하지 않은 값입니다." : fieldError.getDefaultMessage();
        return new FieldErrorDetail(fieldError.getField(), reason);
    }
}
