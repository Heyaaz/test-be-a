package com.example.be_a.class_.application;

import com.example.be_a.class_.domain.ClassEntity;
import com.example.be_a.class_.domain.ClassStatus;
import java.time.LocalDate;

public record ClassDetailView(
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

    public static ClassDetailView from(ClassEntity classEntity, long waitingCount) {
        return new ClassDetailView(
            classEntity.getId(),
            classEntity.getCreatorId(),
            classEntity.getTitle(),
            classEntity.getDescription(),
            classEntity.getPrice(),
            classEntity.getCapacity(),
            classEntity.getEnrolledCount(),
            waitingCount,
            classEntity.getStartDate(),
            classEntity.getEndDate(),
            classEntity.getStatus()
        );
    }
}
