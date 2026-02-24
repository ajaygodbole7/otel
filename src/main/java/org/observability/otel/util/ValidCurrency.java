package org.observability.otel.util;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * Validates that a string represents a valid ISO 4217 currency code.
 *
 * <p>Null values are considered valid. Use {@code @NotNull} in addition to this
 * annotation if null values should be rejected.</p>
 *
 */
@Documented
@Constraint(validatedBy = ValidCurrencyValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidCurrency {
    /**
     * The error message to be used when validation fails.
     */
    String message() default "Invalid ISO 4217 currency code";

    /**
     * The validation groups this constraint belongs to.
     */
    Class<?>[] groups() default {};

    /**
     * Additional payload information.
     */
    Class<? extends Payload>[] payload() default {};
}
