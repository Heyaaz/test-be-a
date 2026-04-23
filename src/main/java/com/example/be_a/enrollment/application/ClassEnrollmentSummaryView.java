package com.example.be_a.enrollment.application;

import com.example.be_a.enrollment.domain.EnrollmentStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ClassEnrollmentSummaryView(
    Long enrollmentId,
    Long userId,
    String userName,
    EnrollmentStatus status,
    LocalDateTime requestedAt,
    LocalDateTime confirmedAt,
    LocalDateTime cancelledAt
) {
}
