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

  @Test
  @DisplayName("should validate valid JSON object")
  void shouldValidateJsonObject() {
    String json = "{\"name\":\"John\",\"age\":30}";
    assertThat(JsonUtils.isJson(json)).isTrue();
  }

  @Test
  @DisplayName("should validate valid JSON array")
  void shouldValidateJsonArray() {
    String json = "[1,2,3,{\"key\":\"value\"}]";
    assertThat(JsonUtils.isJson(json)).isTrue();
  }

  @ParameterizedTest
  @DisplayName("should validate valid simple JSON values")
  @ValueSource(strings = {
      "\"string\"",  // String
      "42",         // Number
      "-42.5",      // Negative number
      "true",       // Boolean true
      "false",      // Boolean false
      "null"        // Null
  })
  void shouldValidateSimpleJsonValues(String json) {
    assertThat(JsonUtils.isJson(json)).isTrue();
  }

  @ParameterizedTest
  @DisplayName("should reject invalid JSON strings")
  @ValueSource(strings = {
      "{key:value}",          // Missing quotes around keys/values
      "{\"key\":value}",      // Missing quotes around value
      "{\"key\":\"value\"",   // Missing closing brace
      "[1,2,3",              // Missing closing bracket
      "{'key':'value'}",     // Single quotes instead of double
      "{\"key\":undefined}", // undefined is not valid JSON
      "function(){}",        // JavaScript function
      "{key: \"value\",}",   // Trailing comma
      "\"string"             // Unclosed string
  })
  void shouldRejectInvalidJson(String invalidJson) {
    assertThat(JsonUtils.isJson(invalidJson)).isFalse();
  }

  @ParameterizedTest
  @DisplayName("should reject JSON with trailing tokens (lenient mode)")
  @ValueSource(strings = {
      "42 garbage",           // number followed by text
      "{} []",                // two root values
      "true extra",           // boolean followed by text
      "null extra",           // null followed by text
      "\"string\" trailing",  // string followed by text
      "[1,2,3] {}"            // array followed by object
  })
  void shouldRejectJsonWithTrailingTokens(String input) {
    assertThat(JsonUtils.isJson(input)).isFalse();
  }

  @ParameterizedTest
  @DisplayName("should throw IllegalArgumentException for null or blank input")
  @NullAndEmptySource
  @ValueSource(strings = {" ", "\t", "\n", "\r", " \n\t\r "})
  void shouldThrowExceptionForNullOrBlankInput(String input) {
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
  @DisplayName("should validate JSON at exactly max size limit")
  void shouldValidateJsonAtSizeLimit() {
    String jsonAtLimit = "\"" + "x".repeat(JsonUtils.MAX_JSON_LENGTH - 2) + "\""; // Account for quotes
    assertThat(JsonUtils.isJson(jsonAtLimit)).isTrue();
  }

  @Test
  @DisplayName("should validate complex nested JSON")
  void shouldValidateComplexJson() {
    String complexJson = """
            {
                "name": "John",
                "age": 30,
                "address": {
                    "street": "123 Main St",
                    "city": "Boston",
                    "coordinates": [42.3601, -71.0589]
                },
                "phoneNumbers": [
                    {"type": "home", "number": "555-1234"},
                    {"type": "work", "number": "555-5678"}
                ],
                "active": true,
                "notes": null
            }
            """;
    assertThat(JsonUtils.isJson(complexJson)).isTrue();
  }

  @Test
  @DisplayName("should validate strict JSON object")
  void shouldValidateStrictJsonObject() {
    String json = "{\"name\":\"John\",\"age\":30}";
    assertThat(JsonUtils.isJson(json, true)).isTrue();
  }

  @Test
  @DisplayName("should validate strict JSON array")
  void shouldValidateStrictJsonArray() {
    String json = "[1,2,3,{\"key\":\"value\"}]";
    assertThat(JsonUtils.isJson(json, true)).isTrue();
  }

  @ParameterizedTest
  @DisplayName("should reject non-object/array root values in strict mode")
  @ValueSource(strings = {
      "\"string\"",     // String at root
      "42",            // Number at root
      "-42.5",         // Negative number at root
      "true",          // Boolean at root
      "false",         // Boolean at root
      "null"           // Null at root
  })
  void shouldRejectNonObjectArrayRootValues(String json) {
    assertThat(JsonUtils.isJson(json, true)).isFalse();
  }

  @ParameterizedTest
  @DisplayName("should reject invalid JSON in strict mode")
  @ValueSource(strings = {
      "{key:value}",           // Missing quotes around keys/values
      "{\"key\":value}",       // Missing quotes around value
      "{\"key\":\"value\"",    // Missing closing brace
      "[1,2,3",               // Missing closing bracket
      "{'key':'value'}",      // Single quotes
      "{\"numbers\":[1,2,3,]}", // Trailing comma
      "{\"key\":\"value\",}",  // Trailing comma in object
      "{\"key\": 01234}",      // Leading zeros in numbers
      "{\"key\": .123}"        // Missing leading zero
  })
  void shouldRejectInvalidStrictJson(String invalidJson) {
    assertThat(JsonUtils.isJson(invalidJson, true)).isFalse();
  }

  @Test
  @DisplayName("should validate complex strict JSON")
  void shouldValidateComplexStrictJson() {
    String complexJson = """
           {
               "name": "John",
               "age": 30,
               "address": {
                   "street": "123 Main St",
                   "city": "Boston",
                   "coordinates": [42.3601, -71.0589]
               },
               "phoneNumbers": [
                   {"type": "home", "number": "555-1234"},
                   {"type": "work", "number": "555-5678"}
               ],
               "active": true,
               "notes": null
           }
           """;
    assertThat(JsonUtils.isJson(complexJson, true)).isTrue();
  }

  @Test
  @DisplayName("should reject invalid complex JSON in strict mode")
  void shouldRejectInvalidComplexStrictJson() {
    String invalidComplexJson = """
           {
               "numbers": [1,2,3,],      // Trailing comma in array
               "integer": 0123,          // Leading zeros
               "decimal": .123,          // Missing leading zero
               "object": {               // Trailing comma in object
                   "key": "value",
               }
           }
           """;
    assertThat(JsonUtils.isJson(invalidComplexJson, true)).isFalse();
  }

  @ParameterizedTest
  @DisplayName("should throw IllegalArgumentException for null or blank input in strict mode")
  @NullAndEmptySource
  @ValueSource(strings = {" ", "\t", "\n", "\r", " \n\t\r "})
  void shouldThrowExceptionForNullOrBlankInputStrict(String input) {
    assertThatThrownBy(() -> JsonUtils.isJson(input, true))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Input String cannot be null/empty/blank");
  }

}
