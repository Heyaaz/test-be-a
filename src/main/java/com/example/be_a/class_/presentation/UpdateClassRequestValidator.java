package com.example.be_a.class_.presentation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class UpdateClassRequestValidator implements ConstraintValidator<ValidUpdateClassRequest, UpdateClassRequest> {

    @Override
    public boolean isValid(UpdateClassRequest value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }

        boolean valid = true;
        context.disableDefaultConstraintViolation();

        valid &= validateTitle(value, context);
        valid &= validateDescription(value, context);
        valid &= validatePrice(value, context);
        valid &= validateCapacity(value, context);
        valid &= validateStartDate(value, context);
        valid &= validateEndDate(value, context);
        valid &= validateDateRange(value, context);

        return valid;
    }

    private boolean validateTitle(UpdateClassRequest value, ConstraintValidatorContext context) {
        if (!value.hasTitle()) {
            return true;
        }
        if (value.getTitle() != null && !value.getTitle().isBlank()) {
            return true;
        }
        addViolation(context, "title", "제목은 필수입니다.");
        return false;
    }

    private boolean validateDescription(UpdateClassRequest value, ConstraintValidatorContext context) {
        if (!value.hasDescription() || value.getDescription() != null) {
            return true;
        }
        addViolation(context, "description", "설명은 null일 수 없습니다.");
        return false;
    }

    private boolean validatePrice(UpdateClassRequest value, ConstraintValidatorContext context) {
        if (!value.hasPrice()) {
            return true;
        }
        if (value.getPrice() == null) {
            addViolation(context, "price", "가격은 필수입니다.");
            return false;
        }
        if (value.getPrice() >= 0) {
            return true;
        }
        addViolation(context, "price", "가격은 0 이상이어야 합니다.");
        return false;
    }

    private boolean validateCapacity(UpdateClassRequest value, ConstraintValidatorContext context) {
        if (!value.hasCapacity()) {
            return true;
        }
        if (value.getCapacity() == null) {
            addViolation(context, "capacity", "정원은 필수입니다.");
            return false;
        }
        if (value.getCapacity() >= 1) {
            return true;
        }
        addViolation(context, "capacity", "정원은 1 이상이어야 합니다.");
        return false;
    }

    private boolean validateStartDate(UpdateClassRequest value, ConstraintValidatorContext context) {
        if (!value.hasStartDate() || value.getStartDate() != null) {
            return true;
        }
        addViolation(context, "startDate", "시작일은 필수입니다.");
        return false;
    }

    private boolean validateEndDate(UpdateClassRequest value, ConstraintValidatorContext context) {
        if (!value.hasEndDate() || value.getEndDate() != null) {
            return true;
        }
        addViolation(context, "endDate", "종료일은 필수입니다.");
        return false;
    }

    private boolean validateDateRange(UpdateClassRequest value, ConstraintValidatorContext context) {
        if (!value.hasStartDate() || !value.hasEndDate()) {
            return true;
        }
        if (value.getStartDate() == null || value.getEndDate() == null) {
            return true;
        }
        if (!value.getEndDate().isBefore(value.getStartDate())) {
            return true;
        }
        addViolation(context, "endDate", "종료일은 시작일보다 빠를 수 없습니다.");
        return false;
    }

    private void addViolation(ConstraintValidatorContext context, String field, String message) {
        context.buildConstraintViolationWithTemplate(message)
            .addPropertyNode(field)
            .addConstraintViolation();
    }
}
