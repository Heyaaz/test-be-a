package com.example.be_a.class_.presentation;

import com.example.be_a.class_.domain.ClassEntity;
import com.example.be_a.class_.domain.ClassStatus;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "강의 생성 응답")
public record CreateClassResponse(
    @Schema(description = "강의 ID", example = "1")
    Long id,
    @Schema(description = "크리에이터 사용자 ID", example = "1")
    Long creatorId,
    @Schema(description = "강의 제목", example = "실전 Spring Boot")
    String title,
    @Schema(description = "가격", example = "50000")
    int price,
    @Schema(description = "최대 수강 정원", example = "30")
    int capacity,
    @Schema(description = "강의 상태", example = "DRAFT")
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
