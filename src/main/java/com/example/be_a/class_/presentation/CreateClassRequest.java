package com.example.be_a.class_.presentation;

import com.example.be_a.class_.application.CreateClassCommand;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

@ValidCreateClassRequest
public record CreateClassRequest(
    @NotBlank(message = "제목은 필수입니다.")
    String title,

    String description,

    @NotNull(message = "가격은 필수입니다.")
    @Min(value = 0, message = "가격은 0 이상이어야 합니다.")
    Integer price,

    @NotNull(message = "정원은 필수입니다.")
    @Min(value = 1, message = "정원은 1 이상이어야 합니다.")
    Integer capacity,

    @NotNull(message = "시작일은 필수입니다.")
    LocalDate startDate,

    @NotNull(message = "종료일은 필수입니다.")
    LocalDate endDate
) {

    public CreateClassCommand toCommand() {
        return new CreateClassCommand(title, description, price, capacity, startDate, endDate);
    }
}
