package com.example.be_a.class_.presentation;

import com.example.be_a.class_.domain.ClassEntity;
import com.example.be_a.class_.domain.ClassStatus;

public record CreateClassResponse(
    Long id,
    Long creatorId,
    String title,
    int price,
    int capacity,
    ClassStatus status
) {

    public static CreateClassResponse from(ClassEntity classEntity) {
        return new CreateClassResponse(
            classEntity.getId(),
            classEntity.getCreatorId(),
            classEntity.getTitle(),
            classEntity.getPrice(),
            classEntity.getCapacity(),
            classEntity.getStatus()
        );
    }
}
