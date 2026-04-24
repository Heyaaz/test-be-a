package com.example.be_a.class_.presentation;

import com.example.be_a.class_.domain.UpdateClassCommand;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

@ValidUpdateClassRequest
@Schema(description = "강의 수정 요청. 전달한 필드만 부분 수정합니다.")
public class UpdateClassRequest {

    @Schema(description = "강의 제목", example = "실전 Spring Boot 심화")
    private String title;

    @Schema(description = "강의 설명", example = "동시성 제어 실습을 추가한 강의")
    private String description;

    @Schema(description = "가격", example = "60000", minimum = "0")
    private Integer price;

    @Schema(description = "최대 수강 정원. 현재 enrolledCount보다 작게 줄일 수 없습니다.", example = "40", minimum = "1")
    private Integer capacity;

    @Schema(description = "강의 시작일", example = "2026-05-01")
    private LocalDate startDate;

    @Schema(description = "강의 종료일", example = "2026-06-15")
    private LocalDate endDate;

    @JsonIgnore
    private boolean titlePresent;

    @JsonIgnore
    private boolean descriptionPresent;

    @JsonIgnore
    private boolean pricePresent;

    @JsonIgnore
    private boolean capacityPresent;

    @JsonIgnore
    private boolean startDatePresent;

    @JsonIgnore
    private boolean endDatePresent;

    public String getTitle() {
        return title;
    }

    @JsonSetter("title")
    public void setTitle(String title) {
        this.title = title;
        this.titlePresent = true;
    }

    public String getDescription() {
        return description;
    }

    @JsonSetter("description")
    public void setDescription(String description) {
        this.description = description;
        this.descriptionPresent = true;
    }

    public Integer getPrice() {
        return price;
    }

    @JsonSetter("price")
    public void setPrice(Integer price) {
        this.price = price;
        this.pricePresent = true;
    }

    public Integer getCapacity() {
        return capacity;
    }

    @JsonSetter("capacity")
    public void setCapacity(Integer capacity) {
        this.capacity = capacity;
        this.capacityPresent = true;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    @JsonSetter("startDate")
    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
        this.startDatePresent = true;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    @JsonSetter("endDate")
    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
        this.endDatePresent = true;
    }

    public boolean hasTitle() {
        return titlePresent;
    }

    public boolean hasDescription() {
        return descriptionPresent;
    }

    public boolean hasPrice() {
        return pricePresent;
    }

    public boolean hasCapacity() {
        return capacityPresent;
    }

    public boolean hasStartDate() {
        return startDatePresent;
    }

    public boolean hasEndDate() {
        return endDatePresent;
    }

    public UpdateClassCommand toCommand() {
        return new UpdateClassCommand(
            title,
            titlePresent,
            description,
            descriptionPresent,
            price,
            pricePresent,
            capacity,
            capacityPresent,
            startDate,
            startDatePresent,
            endDate,
            endDatePresent
        );
    }
}
