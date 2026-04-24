package com.example.be_a.class_.presentation;

import com.example.be_a.class_.application.CreateClassCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

@ValidCreateClassRequest
@Schema(description = "강의 생성 요청")
public record CreateClassRequest(
    @Schema(description = "강의 제목", example = "실전 Spring Boot", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "제목은 필수입니다.")
    String title,

    @Schema(description = "강의 설명", example = "JPA와 동시성 제어를 다루는 실전 강의")
    String description,

    @Schema(description = "가격", example = "50000", minimum = "0", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "가격은 필수입니다.")
    @Min(value = 0, message = "가격은 0 이상이어야 합니다.")
    Integer price,

    @Schema(description = "최대 수강 정원", example = "30", minimum = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "정원은 필수입니다.")
    @Min(value = 1, message = "정원은 1 이상이어야 합니다.")
    Integer capacity,

    @Schema(description = "강의 시작일", example = "2026-05-01", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "시작일은 필수입니다.")
    LocalDate startDate,

    @Schema(description = "강의 종료일", example = "2026-05-31", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "종료일은 필수입니다.")
    LocalDate endDate
) {

    public CreateClassCommand toCommand() {
        return new CreateClassCommand(title, description, price, capacity, startDate, endDate);
    }
}
