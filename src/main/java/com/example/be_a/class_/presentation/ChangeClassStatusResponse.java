package com.example.be_a.class_.presentation;

import com.example.be_a.class_.domain.ClassEntity;
import com.example.be_a.class_.domain.ClassStatus;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "강의 상태 전환 응답")
public record ChangeClassStatusResponse(
    @Schema(description = "강의 ID", example = "1")
    Long id,
    @Schema(description = "전환 후 강의 상태", example = "OPEN")
    ClassStatus status
) {

    public static ChangeClassStatusResponse from(ClassEntity classEntity) {
        return new ChangeClassStatusResponse(classEntity.getId(), classEntity.getStatus());
    }
}
