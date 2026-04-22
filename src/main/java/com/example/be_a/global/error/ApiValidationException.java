package com.example.be_a.global.error;

import java.util.List;

public class ApiValidationException extends RuntimeException {

    private final List<FieldErrorDetail> fieldErrors;

    private ApiValidationException(List<FieldErrorDetail> fieldErrors) {
        super(ErrorCode.VALIDATION_ERROR.getMessage());
        this.fieldErrors = List.copyOf(fieldErrors);
    }

    public static ApiValidationException of(String field, String reason) {
        return new ApiValidationException(List.of(new FieldErrorDetail(field, reason)));
    }

    public List<FieldErrorDetail> getFieldErrors() {
        return fieldErrors;
    }
}
