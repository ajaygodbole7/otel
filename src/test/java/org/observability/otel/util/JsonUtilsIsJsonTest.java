package org.observability.otel.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("JsonUtils.isJson(String)")
class JsonUtilsIsJsonTest {

  private static final String OVERSIZED_JSON = "x".repeat(JsonUtils.MAX_JSON_LENGTH + 1);

  // ── Lenient mode (isJson(String)) ────────────────────────────────────────

  @Test
  @DisplayName("should validate JSON object")
  void shouldValidateJsonObject() {
    assertThat(JsonUtils.isJson("{\"name\":\"John\",\"age\":30}")).isTrue();
  }

  @Test
  @DisplayName("should validate JSON array")
  void shouldValidateJsonArray() {
    assertThat(JsonUtils.isJson("[1,2,3,{\"key\":\"value\"}]")).isTrue();
  }

  @ParameterizedTest
  @DisplayName("should validate primitive JSON values at root")
  @ValueSource(strings = {"\"string\"", "42", "true"})
  void shouldValidatePrimitiveJsonValues(String json) {
    assertThat(JsonUtils.isJson(json)).isTrue();
  }

  @ParameterizedTest
  @DisplayName("should reject invalid JSON")
  @ValueSource(strings = {
      "{key:value}",          // unquoted keys
      "{\"key\":\"value\"",   // missing closing brace
      "{'key':'value'}"       // single quotes
  })
  void shouldRejectInvalidJson(String invalidJson) {
    assertThat(JsonUtils.isJson(invalidJson)).isFalse();
  }

  @ParameterizedTest
  @DisplayName("should reject JSON with trailing tokens")
  @ValueSource(strings = {
      "42 garbage",   // primitive + trailing text
      "{} []"         // two root values
  })
  void shouldRejectJsonWithTrailingTokens(String input) {
    assertThat(JsonUtils.isJson(input)).isFalse();
  }

  @ParameterizedTest
  @DisplayName("should throw for null or blank input")
  @NullAndEmptySource
  @ValueSource(strings = {" "})
  void shouldThrowForNullOrBlankInput(String input) {
    assertThatThrownBy(() -> JsonUtils.isJson(input))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("should reject oversized JSON")
  void shouldRejectOversizedJson() {
    assertThatThrownBy(() -> JsonUtils.isJson(OVERSIZED_JSON))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("should accept JSON at exactly the size limit")
  void shouldAcceptJsonAtSizeLimit() {
    String jsonAtLimit = "\"" + "x".repeat(JsonUtils.MAX_JSON_LENGTH - 2) + "\"";
    assertThat(JsonUtils.isJson(jsonAtLimit)).isTrue();
  }

  // ── Strict mode (isJson(String, true)) ───────────────────────────────────

  @Test
  @DisplayName("should validate object and array in strict mode")
  void shouldValidateStrictMode() {
    assertThat(JsonUtils.isJson("{\"name\":\"John\"}", true)).isTrue();
    assertThat(JsonUtils.isJson("[1,2,3]", true)).isTrue();
  }

  @ParameterizedTest
  @DisplayName("should reject primitive root values in strict mode")
  @ValueSource(strings = {"\"string\"", "42", "true"})
  void shouldRejectPrimitiveRootInStrictMode(String json) {
    assertThat(JsonUtils.isJson(json, true)).isFalse();
  }

  @ParameterizedTest
  @DisplayName("should reject strict-mode violations (trailing commas, leading zeros)")
  @ValueSource(strings = {
      "{\"key\":\"value\",}",      // trailing comma in object
      "{\"numbers\":[1,2,3,]}",   // trailing comma in array
      "{\"key\": 01234}"          // leading zeros in number
  })
  void shouldRejectStrictModeViolations(String invalidJson) {
    assertThat(JsonUtils.isJson(invalidJson, true)).isFalse();
  }

  @Test
  @DisplayName("should reject complex JSON with multiple strict violations")
  void shouldRejectComplexInvalidStrictJson() {
    String invalid = """
        {
            "numbers": [1,2,3,],
            "integer": 0123,
            "object": {"key": "value",}
        }
        """;
    assertThat(JsonUtils.isJson(invalid, true)).isFalse();
  }
}
