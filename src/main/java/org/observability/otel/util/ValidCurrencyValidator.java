package org.observability.otel.util;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Currency;
import java.util.Locale;

/**
 * Implementation of the ISO 4217 currency code validator.
 */
public class ValidCurrencyValidator implements ConstraintValidator<ValidCurrency, String> {

    /**
     * Required no-args constructor for Jakarta Validation
     */
    public ValidCurrencyValidator() {
        // Empty constructor required by Jakarta Validation
    }

    @Override
    public void initialize(ValidCurrency constraintAnnotation) {
        //no initialization needed
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true; // null values are handled by @NotNull if needed
        }

        if (value.trim().isEmpty()) {
            return false;
        }

        try {
            // Normalize and validate against ISO 4217
            String normalizedCode = value.trim().toUpperCase(Locale.ENGLISH);
            Currency.getInstance(normalizedCode);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
