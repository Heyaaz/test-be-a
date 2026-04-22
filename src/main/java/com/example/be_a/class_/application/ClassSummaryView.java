package com.example.be_a.class_.application;

import com.example.be_a.class_.domain.ClassEntity;
import com.example.be_a.class_.domain.ClassStatus;

public record ClassSummaryView(
    Long id,
    Long creatorId,
    String title,
    int price,
    int capacity,
    int enrolledCount,
    long waitingCount,
    ClassStatus status
) {

    public static ClassSummaryView from(ClassEntity classEntity, long waitingCount) {
        return new ClassSummaryView(
            classEntity.getId(),
            classEntity.getCreatorId(),
            classEntity.getTitle(),
            classEntity.getPrice(),
            classEntity.getCapacity(),
            classEntity.getEnrolledCount(),
            waitingCount,
            classEntity.getStatus()
        );
    }
}
