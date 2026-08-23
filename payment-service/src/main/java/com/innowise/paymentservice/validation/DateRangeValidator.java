package com.innowise.paymentservice.validation;

import com.innowise.paymentservice.dto.PaymentTotalFilterDto;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class DateRangeValidator implements ConstraintValidator<ValidDateRange, PaymentTotalFilterDto> {

    @Override
    public boolean isValid(PaymentTotalFilterDto filter, ConstraintValidatorContext context) {
        if (filter == null || filter.from() == null || filter.to() == null) {
            return true;
        }
        if (!filter.from().isAfter(filter.to())) {
            return true;
        }
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                .addPropertyNode("from")
                .addConstraintViolation();
        return false;
    }
}
