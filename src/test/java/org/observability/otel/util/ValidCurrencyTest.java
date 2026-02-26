package org.observability.otel.util;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
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

  private static class TestClass {
    @ValidCurrency
    private String currencyCode;

    public TestClass(String currencyCode) {
      this.currencyCode = currencyCode;
    }
  }

  @ParameterizedTest
  @DisplayName("Should validate common currency codes")
  @ValueSource(strings = {"USD", "EUR", "GBP", "JPY"})
  void shouldValidateCommonCurrencyCodes(String code) {
    assertThat(validator.validate(new TestClass(code))).isEmpty();
  }

  @ParameterizedTest
  @DisplayName("Should accept currency codes regardless of case")
  @ValueSource(strings = {"usd", "eUr", "GBP"})
  void shouldHandleCaseInsensitivity(String code) {
    assertThat(validator.validate(new TestClass(code))).isEmpty();
  }

  @ParameterizedTest
  @DisplayName("Should accept currency codes with surrounding whitespace")
  @ValueSource(strings = {" USD", "EUR ", " JPY "})
  void shouldHandleWhitespace(String code) {
    assertThat(validator.validate(new TestClass(code))).isEmpty();
  }

  @ParameterizedTest
  @DisplayName("Should reject invalid currency codes")
  @ValueSource(strings = {"XYZ", "ABC", "INVALID", "US", "USDD", "123", "USD1", "1USD", "US-D"})
  void shouldRejectInvalidCurrencyCodes(String invalidCode) {
    Set<ConstraintViolation<TestClass>> violations = validator.validate(new TestClass(invalidCode));

    assertThat(violations).hasSize(1);
    ConstraintViolation<TestClass> violation = violations.iterator().next();
    assertThat(violation.getMessage()).isEqualTo("Invalid ISO 4217 currency code");
    assertThat(violation.getPropertyPath().toString()).isEqualTo("currencyCode");
  }

  @Test
  @DisplayName("Should reject empty strings")
  void shouldRejectEmptyStrings() {
    assertThat(validator.validate(new TestClass(""))).hasSize(1);
    assertThat(validator.validate(new TestClass("   "))).hasSize(1);
  }

  @Test
  @DisplayName("Should accept null value")
  void shouldAcceptNullValue() {
    // null validation is handled separately by @NotNull; @ValidCurrency passes null through
    assertThat(validator.validate(new TestClass(null))).isEmpty();
  }
}
