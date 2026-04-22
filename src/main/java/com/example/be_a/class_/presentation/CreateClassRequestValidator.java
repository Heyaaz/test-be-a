package com.example.be_a.class_.presentation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class CreateClassRequestValidator implements ConstraintValidator<ValidCreateClassRequest, CreateClassRequest> {

    @Override
    public boolean isValid(CreateClassRequest value, ConstraintValidatorContext context) {
        if (value == null || value.startDate() == null || value.endDate() == null) {
            return true;
        }

        if (!value.endDate().isBefore(value.startDate())) {
            return true;
        }

        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
            .addPropertyNode("endDate")
            .addConstraintViolation();
        return false;
    }
}
