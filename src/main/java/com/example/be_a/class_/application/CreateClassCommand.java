package com.example.be_a.class_.application;

import java.time.LocalDate;

public record CreateClassCommand(
    String title,
    String description,
    int price,
    int capacity,
    LocalDate startDate,
    LocalDate endDate
) {
}
