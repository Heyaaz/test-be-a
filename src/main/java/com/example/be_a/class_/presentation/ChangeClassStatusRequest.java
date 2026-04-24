package com.example.be_a.class_.presentation;

import com.example.be_a.class_.domain.ClassStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "강의 상태 전환 요청")
public record ChangeClassStatusRequest(
    @Schema(description = "목표 강의 상태", example = "OPEN", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "변경할 상태는 필수입니다.")
    ClassStatus targetStatus
) {
}
