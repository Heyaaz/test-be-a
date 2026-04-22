package com.example.be_a.enrollment.presentation;

import com.example.be_a.enrollment.domain.EnrollmentEntity;
import com.example.be_a.enrollment.domain.EnrollmentStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record EnrollmentResponse(
    Long id,
    Long classId,
    Long userId,
    EnrollmentStatus status,
    LocalDateTime requestedAt,
    LocalDateTime confirmedAt,
    LocalDateTime cancelledAt
) {

    public static EnrollmentResponse from(EnrollmentEntity enrollment) {
        return new EnrollmentResponse(
            enrollment.getId(),
            enrollment.getClassId(),
            enrollment.getUserId(),
            enrollment.getStatus(),
            enrollment.getRequestedAt(),
            enrollment.getConfirmedAt(),
            enrollment.getCancelledAt()
        );
    }
}
