package com.example.be_a.enrollment.application;

public record ApplyEnrollmentCommand(
    Long classId,
    boolean waitlist
) {
}
