package com.example.be_a.class_.presentation;

import com.example.be_a.class_.application.ClassSummaryView;
import com.example.be_a.class_.domain.ClassStatus;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "강의 목록 응답 항목")
public record ClassSummaryResponse(
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
    @Schema(description = "현재 정원 차감 대상 인원(PENDING + CONFIRMED)", example = "12")
    int enrolledCount,
    @Schema(description = "대기열 인원(WAITING)", example = "3")
    long waitingCount,
    @Schema(description = "강의 상태", example = "OPEN")
    ClassStatus status
) {

    public static ClassSummaryResponse from(ClassSummaryView summaryView) {
        return new ClassSummaryResponse(
            summaryView.id(),
            summaryView.creatorId(),
            summaryView.title(),
            summaryView.price(),
            summaryView.capacity(),
            summaryView.enrolledCount(),
            summaryView.waitingCount(),
            summaryView.status()
        );
    }
}
