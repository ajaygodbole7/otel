package org.observability.otel.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.jayway.jsonpath.InvalidJsonException;
import com.jayway.jsonpath.InvalidPathException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("JsonUtils.extractValue")
class JsonUtilsExtractValueTest {

  private static final String TEST_JSON = """
        {
            "stringValue": "test",
            "numberValue": 42,
            "booleanValue": true,
            "nullValue": null,
            "arrayValue": [1, 2, 3],
            "objectValue": {
                "nestedField": "nested"
            }
        }
        """;

  // Success Cases - Focus on path extraction, not type conversion

  @Test
  @DisplayName("should extract existing value")
  void shouldExtractExistingValue() throws JsonProcessingException {
    Optional<Object> result = JsonUtils.extractValue(TEST_JSON, "$.stringValue");
    assertThat(result)
        .isPresent()
        .contains("test");
  }

  @Test
  @DisplayName("should extract from nested path")
  void shouldExtractFromNestedPath() throws JsonProcessingException {
    Optional<Object> result = JsonUtils.extractValue(TEST_JSON, "$.objectValue.nestedField");
    assertThat(result)
        .isPresent()
        .contains("nested");
  }

  @Test
  @DisplayName("should extract array element")
  void shouldExtractArrayElement() throws JsonProcessingException {
    Optional<Object> result = JsonUtils.extractValue(TEST_JSON, "$.arrayValue[1]");
    assertThat(result)
        .isPresent()
        .contains(2);
  }

  // Null/Empty Cases

  @Test
  @DisplayName("should return empty optional for non-existent path")
  void shouldReturnEmptyForNonExistentPath() throws JsonProcessingException {
    Optional<Object> result = JsonUtils.extractValue(TEST_JSON, "$.nonexistent");
    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("should return empty optional for null value")
  void shouldReturnEmptyForNullValue() throws JsonProcessingException {
    Optional<Object> result = JsonUtils.extractValue(TEST_JSON, "$.nullValue");
    assertThat(result).isEmpty();
  }

  // Exception Cases

  @ParameterizedTest
  @DisplayName("should throw IllegalArgumentException for null or blank JSON")
  @NullAndEmptySource
  @ValueSource(strings = {" "})
  void shouldThrowExceptionForInvalidJson(String json) {
    assertThatThrownBy(() -> JsonUtils.extractValue(json, "$.field"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("json must not be null or blank");
  }

  @ParameterizedTest
  @DisplayName("should throw IllegalArgumentException for null or blank path")
  @NullAndEmptySource
  @ValueSource(strings = {" "})
  void shouldThrowExceptionForInvalidPath(String path) {
    assertThatThrownBy(() -> JsonUtils.extractValue(TEST_JSON, path))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("JSONPath expression must not be null");
  }

  @Test
  @DisplayName("should throw InvalidPathException for malformed path")
  void shouldThrowExceptionForMalformedPath() {
    assertThatThrownBy(() -> JsonUtils.extractValue(TEST_JSON, "$["))
        .isInstanceOf(InvalidPathException.class);
  }

  @Test
  @DisplayName("should throw exception for invalid JSON")
  void shouldThrowExceptionForInvalidJson() {
    String invalidJson = "{ invalid json }";
    assertThatThrownBy(() -> JsonUtils.extractValue(invalidJson, "$.field"))
        .isInstanceOf(InvalidJsonException.class);
  }

  @Test
  @DisplayName("should throw exception for oversized JSON")
  void shouldThrowExceptionForOversizedJson() {
    String oversizedJson = "x".repeat(JsonUtils.MAX_JSON_LENGTH + 1);
    assertThatThrownBy(() -> JsonUtils.extractValue(oversizedJson, "$.field"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("JSON exceeds maximum allowed length");
  }
}
