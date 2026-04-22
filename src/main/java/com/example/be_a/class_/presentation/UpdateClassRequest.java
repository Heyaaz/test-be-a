package com.example.be_a.class_.presentation;

import com.example.be_a.class_.domain.UpdateClassCommand;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import java.time.LocalDate;

@ValidUpdateClassRequest
public class UpdateClassRequest {

    private String title;
    private String description;
    private Integer price;
    private Integer capacity;
    private LocalDate startDate;
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
