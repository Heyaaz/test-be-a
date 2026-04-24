package com.example.be_a.class_.presentation;

import com.example.be_a.class_.application.ClassDetailView;
import com.example.be_a.class_.domain.ClassStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

@Schema(description = "강의 상세 응답")
public record ClassDetailResponse(
    @Schema(description = "강의 ID", example = "1")
    Long id,
    @Schema(description = "크리에이터 사용자 ID", example = "1")
    Long creatorId,
    @Schema(description = "강의 제목", example = "실전 Spring Boot")
    String title,
    @Schema(description = "강의 설명", example = "JPA와 동시성 제어를 다루는 실전 강의")
    String description,
    @Schema(description = "가격", example = "50000")
    int price,
    @Schema(description = "최대 수강 정원", example = "30")
    int capacity,
    @Schema(description = "현재 정원 차감 대상 인원(PENDING + CONFIRMED)", example = "12")
    int enrolledCount,
    @Schema(description = "대기열 인원(WAITING)", example = "3")
    long waitingCount,
    @Schema(description = "강의 시작일", example = "2026-05-01")
    LocalDate startDate,
    @Schema(description = "강의 종료일", example = "2026-05-31")
    LocalDate endDate,
    @Schema(description = "강의 상태", example = "OPEN")
    ClassStatus status
) {

    public static ClassDetailResponse from(ClassDetailView detailView) {
        return new ClassDetailResponse(
            detailView.id(),
            detailView.creatorId(),
            detailView.title(),
            detailView.description(),
            detailView.price(),
            detailView.capacity(),
            detailView.enrolledCount(),
            detailView.waitingCount(),
            detailView.startDate(),
            detailView.endDate(),
            detailView.status()
        );
    }
}
