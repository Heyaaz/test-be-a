package com.example.be_a.class_.presentation;

import com.example.be_a.class_.application.ClassDetailView;
import com.example.be_a.class_.domain.ClassStatus;
import java.time.LocalDate;

public record ClassDetailResponse(
    Long id,
    Long creatorId,
    String title,
    String description,
    int price,
    int capacity,
    int enrolledCount,
    long waitingCount,
    LocalDate startDate,
    LocalDate endDate,
    ClassStatus status
) {

    public static ClassDetailResponse from(ClassDetailView detailView) {
        return new ClassDetailResponse(
            detailView.id(),
            detailView.creatorId(),
            detailView.title(),
            detailView.description(),
            detailView.price(),
            detailView.capacity(),
            detailView.enrolledCount(),
            detailView.waitingCount(),
            detailView.startDate(),
            detailView.endDate(),
            detailView.status()
        );
    }
}
