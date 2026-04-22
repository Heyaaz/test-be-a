package com.example.be_a.global.error;

import java.util.List;

public record ErrorResponse(
    String code,
    String message,
    String path,
    List<FieldErrorDetail> fieldErrors
) {

    public static ErrorResponse of(ErrorCode errorCode, String path) {
        return new ErrorResponse(errorCode.name(), errorCode.getMessage(), path, List.of());
    }

    public static ErrorResponse of(ErrorCode errorCode, String path, List<FieldErrorDetail> fieldErrors) {
        return new ErrorResponse(errorCode.name(), errorCode.getMessage(), path, List.copyOf(fieldErrors));
    }
}
