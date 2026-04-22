package com.example.be_a.class_.presentation;

import com.example.be_a.class_.domain.ClassStatus;
import jakarta.validation.constraints.NotNull;

public record ChangeClassStatusRequest(
    @NotNull(message = "변경할 상태는 필수입니다.")
    ClassStatus targetStatus
) {
}
