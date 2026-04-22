package com.example.be_a.class_.presentation;

import com.example.be_a.class_.domain.ClassEntity;
import com.example.be_a.class_.domain.ClassStatus;

public record ChangeClassStatusResponse(
    Long id,
    ClassStatus status
) {

    public static ChangeClassStatusResponse from(ClassEntity classEntity) {
        return new ChangeClassStatusResponse(classEntity.getId(), classEntity.getStatus());
    }
}
