package org.observability.otel.util;


import static org.assertj.core.api.Assertions.*;

import java.util.Set;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Unit tests for the {@link ValidCurrency} annotation.
 */
class ValidCurrencyTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    /**
     * Simple test class with a field annotated with @ValidCurrency.
     */
    private static class TestClass {
        @ValidCurrency
        private String currencyCode;

        public TestClass(String currencyCode) {
            this.currencyCode = currencyCode;
        }
    }

    @Test
    @DisplayName("Should validate common currency codes")
    void shouldValidateCommonCurrencyCodes() {
        // Given - instances with valid currency codes
        TestClass usd = new TestClass("USD");
        TestClass eur = new TestClass("EUR");
        TestClass gbp = new TestClass("GBP");
        TestClass jpy = new TestClass("JPY");

        // When - we validate them
        Set<ConstraintViolation<TestClass>> usdViolations = validator.validate(usd);
        Set<ConstraintViolation<TestClass>> eurViolations = validator.validate(eur);
        Set<ConstraintViolation<TestClass>> gbpViolations = validator.validate(gbp);
        Set<ConstraintViolation<TestClass>> jpyViolations = validator.validate(jpy);

        // Then - there should be no violations
        assertThat(usdViolations).isEmpty();
        assertThat(eurViolations).isEmpty();
        assertThat(gbpViolations).isEmpty();
        assertThat(jpyViolations).isEmpty();
    }

    @Test
    @DisplayName("Should handle case insensitivity")
    void shouldHandleCaseInsensitivity() {
        // Given - instances with different case variations
        TestClass lowercase = new TestClass("usd");
        TestClass mixedCase = new TestClass("eUr");
        TestClass uppercase = new TestClass("GBP");

        // When - we validate them
        Set<ConstraintViolation<TestClass>> lowercaseViolations = validator.validate(lowercase);
        Set<ConstraintViolation<TestClass>> mixedCaseViolations = validator.validate(mixedCase);
        Set<ConstraintViolation<TestClass>> uppercaseViolations = validator.validate(uppercase);

        // Then - all should be valid regardless of case
        assertThat(lowercaseViolations).isEmpty();
        assertThat(mixedCaseViolations).isEmpty();
        assertThat(uppercaseViolations).isEmpty();
    }

    @Test
    @DisplayName("Should handle whitespace")
    void shouldHandleWhitespace() {
        // Given - instances with whitespace
        TestClass leadingSpace = new TestClass(" USD");
        TestClass trailingSpace = new TestClass("EUR ");
        TestClass bothSpace = new TestClass(" JPY ");

        // When - we validate them
        Set<ConstraintViolation<TestClass>> leadingViolations = validator.validate(leadingSpace);
        Set<ConstraintViolation<TestClass>> trailingViolations = validator.validate(trailingSpace);
        Set<ConstraintViolation<TestClass>> bothViolations = validator.validate(bothSpace);

        // Then - all should be valid after trimming
        assertThat(leadingViolations).isEmpty();
        assertThat(trailingViolations).isEmpty();
        assertThat(bothViolations).isEmpty();
    }

    @ParameterizedTest
    @DisplayName("Should reject invalid currency codes")
    @ValueSource(strings = {"XYZ", "ABC", "INVALID", "US", "USDD", "123", "USD1", "1USD", "US-D"})
    void shouldRejectInvalidCurrencyCodes(String invalidCode) {
        // Given - instance with invalid code
        TestClass invalid = new TestClass(invalidCode);

        // When - we validate it
        Set<ConstraintViolation<TestClass>> violations = validator.validate(invalid);

        // Then - should have one violation
        assertThat(violations).hasSize(1);

        // And - violation should have correct message
        ConstraintViolation<TestClass> violation = violations.iterator().next();
        assertThat(violation.getMessage()).isEqualTo("Invalid ISO 4217 currency code");

        // And - violation should be on the currencyCode field
        assertThat(violation.getPropertyPath().toString()).isEqualTo("currencyCode");
    }

    @Test
    @DisplayName("Should reject empty strings")
    void shouldRejectEmptyStrings() {
        // Given - instances with empty strings
        TestClass empty = new TestClass("");
        TestClass onlySpaces = new TestClass("   ");

        // When - we validate them
        Set<ConstraintViolation<TestClass>> emptyViolations = validator.validate(empty);
        Set<ConstraintViolation<TestClass>> spacesViolations = validator.validate(onlySpaces);

        // Then - both should be invalid
        assertThat(emptyViolations).hasSize(1);
        assertThat(spacesViolations).hasSize(1);
    }

    @Test
    @DisplayName("Should accept null value")
    void shouldAcceptNullValue() {
        // Given - instance with null value
        TestClass nullValue = new TestClass(null);

        // When - we validate it
        Set<ConstraintViolation<TestClass>> violations = validator.validate(nullValue);

        // Then - should be valid (null validation is separate)
        assertThat(violations).isEmpty();
    }
}
