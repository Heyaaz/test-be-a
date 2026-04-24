package com.example.be_a.global.support;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.springframework.data.domain.Page;

@Schema(description = "페이지 응답")
public record PageResponse<T>(
    @Schema(description = "현재 페이지 항목 목록")
    List<T> items,
    @Schema(description = "현재 페이지 번호(0부터 시작)", example = "0")
    int page,
    @Schema(description = "페이지 크기", example = "20")
    int size,
    @Schema(description = "전체 항목 수", example = "120")
    long totalElements,
    @Schema(description = "전체 페이지 수", example = "6")
    int totalPages,
    @Schema(description = "다음 페이지 존재 여부", example = "true")
    boolean hasNext
) {

    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(
            page.getContent(),
            page.getNumber(),
            page.getSize(),
            page.getTotalElements(),
            page.getTotalPages(),
            page.hasNext()
        );
    }
}
