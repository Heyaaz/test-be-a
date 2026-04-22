package com.example.be_a.class_.presentation;

import com.example.be_a.class_.application.ClassSummaryView;
import com.example.be_a.class_.domain.ClassStatus;

public record ClassSummaryResponse(
    Long id,
    Long creatorId,
    String title,
    int price,
    int capacity,
    int enrolledCount,
    long waitingCount,
    ClassStatus status
) {

    public static ClassSummaryResponse from(ClassSummaryView summaryView) {
        return new ClassSummaryResponse(
            summaryView.id(),
            summaryView.creatorId(),
            summaryView.title(),
            summaryView.price(),
            summaryView.capacity(),
            summaryView.enrolledCount(),
            summaryView.waitingCount(),
            summaryView.status()
        );
    }
}
