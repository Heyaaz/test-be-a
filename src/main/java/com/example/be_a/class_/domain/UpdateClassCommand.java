package com.example.be_a.class_.domain;

import java.time.LocalDate;

public record UpdateClassCommand(
    String title,
    boolean titlePresent,
    String description,
    boolean descriptionPresent,
    Integer price,
    boolean pricePresent,
    Integer capacity,
    boolean capacityPresent,
    LocalDate startDate,
    boolean startDatePresent,
    LocalDate endDate,
    boolean endDatePresent
) {

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
}
