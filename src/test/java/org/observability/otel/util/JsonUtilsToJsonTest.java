package org.observability.otel.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("JsonUtils.toJson methods")
class JsonUtilsToJsonTest {

  private static final String OVERSIZED_JSON = "x".repeat(JsonUtils.MAX_JSON_LENGTH + 1);

  @Test
  @DisplayName("should convert valid JSON object to strict format")
  void shouldConvertValidJsonObjectToStrictFormat() throws JsonProcessingException {
    String input = "{ \"name\" : \"John\" , \"age\" : 30 }";
    String strictJson = JsonUtils.toStrictJson(input);

    assertThat(JsonUtils.isJson(strictJson, true)).isTrue();
    assertThat(strictJson)
        .contains("\"name\"")
        .contains("\"age\"")
        .contains("30");

    // Verify no extra whitespace
    assertThat(strictJson).doesNotContain(" : ");
  }

  @Test
  @DisplayName("should convert valid JSON array to strict format")
  void shouldConvertValidJsonArrayToStrictFormat() throws JsonProcessingException {
    String input = "[ 1, \"text\" , true , null ]";
    String strictJson = JsonUtils.toStrictJson(input);

    assertThat(JsonUtils.isJson(strictJson, true)).isTrue();
    assertThat(strictJson)
        .startsWith("[")
        .endsWith("]")
        .contains("1")
        .contains("\"text\"")
        .contains("true")
        .contains("null");
  }

  @Test
  @DisplayName("should validate root element constraints in strict mode")
  void shouldValidateRootElementConstraints() throws JsonProcessingException {
    // Valid cases - object and array roots
    assertThat(JsonUtils.toStrictJson("{\"key\":\"value\"}")).isNotNull();
    assertThat(JsonUtils.toStrictJson("[1,2,3]")).isNotNull();

    // Document any non-object/array root handling
    /* Note: While RFC 4627 traditionally required object/array roots,
       the implementation accepts primitive roots while maintaining
       other strict formatting rules */
    assertThat(JsonUtils.toStrictJson("\"string\"")).isNotNull();
    assertThat(JsonUtils.toStrictJson("42")).isNotNull();
    assertThat(JsonUtils.toStrictJson("true")).isNotNull();
  }

  @Test
  @DisplayName("should throw exception for invalid JSON syntax")
  void shouldThrowExceptionForInvalidJsonSyntax() {
    assertThatThrownBy(() -> JsonUtils.toStrictJson("{key: \"value\"}"))
        .isInstanceOf(JsonProcessingException.class);

    assertThatThrownBy(() -> JsonUtils.toStrictJson("[1, 2, 3,]")) // trailing comma
        .isInstanceOf(JsonProcessingException.class);

    assertThatThrownBy(() -> JsonUtils.toStrictJson("{\"key\": 'value'}")) // single quotes
        .isInstanceOf(JsonProcessingException.class);
  }

  @Test
  @DisplayName("should throw exception for oversized JSON")
  void shouldThrowExceptionForOversizedJsonInStrictConversion() {
    assertThatThrownBy(() -> JsonUtils.toStrictJson(OVERSIZED_JSON))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("JSON exceeds maximum allowed length");
  }

  @Test
  @DisplayName("should throw exception for null or blank input")
  void shouldThrowExceptionForNullOrBlankInputInStrictConversion() {
    assertThatThrownBy(() -> JsonUtils.toStrictJson(null))
        .isInstanceOf(IllegalArgumentException.class);

    assertThatThrownBy(() -> JsonUtils.toStrictJson(""))
        .isInstanceOf(IllegalArgumentException.class);

    assertThatThrownBy(() -> JsonUtils.toStrictJson("   "))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("should handle complex nested structures")
  void shouldHandleComplexNestedStructures() throws JsonProcessingException {
    String input = """
            {
                "name": "John",
                "details": {
                    "age": 30,
                    "scores": [85, 90, 95],
                    "address": {
                        "street": "123 Main St",
                        "city": "Boston"
                    }
                },
                "active": true,
                "notes": null
            }
            """;

    String strictJson = JsonUtils.toStrictJson(input);

    assertThat(JsonUtils.isJson(strictJson, true)).isTrue();

    // Verify structure is maintained
    assertThat(strictJson)
        .contains("\"name\"")
        .contains("\"details\"")
        .contains("\"scores\"")
        .contains("\"address\"")
        .contains("\"street\"")
        .contains("\"city\"")
        .contains("\"active\"")
        .contains("\"notes\"");

    // Verify values are preserved
    assertThat(strictJson)
        .contains("\"John\"")
        .contains("30")
        .contains("[85,90,95]")
        .contains("\"123 Main St\"")
        .contains("\"Boston\"")
        .contains("true")
        .contains("null");
  }

  @Test
  @DisplayName("should handle number formats in strict mode")
  void shouldHandleNumberFormats() throws JsonProcessingException {
    String input = """
        {
            "integer": 1234,
            "decimal": 0.123,
            "scientific": 1.23e2,
            "negativeScientific": -1.23e-2,
            "negative": -42,
            "fraction": 3.14159,
            "largeInteger": 999999999,
            "smallDecimal": 0.0000001
        }
        """;

    String strictJson = JsonUtils.toStrictJson(input);

    // Verify it's valid strict JSON
    assertThat(JsonUtils.isJson(strictJson, true)).isTrue();

    // Parse and verify exact number values
    Map<String, Object> parsed = JsonUtils.fromJson(strictJson, Map.class);
    assertThat(parsed)
        .hasSize(8)
        .containsEntry("integer", 1234)
        .containsEntry("decimal", 0.123)
        .containsEntry("negative", -42)
        .containsEntry("fraction", 3.14159)
        .containsEntry("smallDecimal", 0.0000001)
        .containsEntry("largeInteger", 999999999);

    // Scientific notation may be normalized, verify the actual values
    assertThat((Double) parsed.get("scientific")).isEqualTo(123.0);
    assertThat((Double) parsed.get("negativeScientific")).isEqualTo(-0.0123);
  }

  @Test
  @DisplayName("should reject invalid number formats in strict mode")
  void shouldRejectInvalidNumberFormats() {
    // Invalid number format (letter in number)
    assertThatThrownBy(() -> JsonUtils.toStrictJson("{\"num\": 12a34}"))
        .isInstanceOf(JsonProcessingException.class)
        .hasMessageContaining("Failed to convert to strict JSON");

    // Invalid decimal format
    assertThatThrownBy(() -> JsonUtils.toStrictJson("{\"num\": 42..42}"))
        .isInstanceOf(JsonProcessingException.class)
        .hasMessageContaining("Failed to convert to strict JSON");

    // Number with invalid character
    assertThatThrownBy(() -> JsonUtils.toStrictJson("{\"num\": 42@42}"))
        .isInstanceOf(JsonProcessingException.class)
        .hasMessageContaining("Failed to convert to strict JSON");

    // Multiple decimal points
    assertThatThrownBy(() -> JsonUtils.toStrictJson("{\"num\": 42.42.42}"))
        .isInstanceOf(JsonProcessingException.class)
        .hasMessageContaining("Failed to convert to strict JSON");
  }


  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  static class TestPerson {
    private String name;
    private int age;
    private LocalDateTime birthDate;
    private List<String> hobbies;
    private Map<String, Object> attributes;
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  static class CircularReference {
    private String name;
    private CircularReference parent;
  }

  @Test
  @DisplayName("should serialize null to 'null' string")
  void shouldSerializeNullToNullString() throws JsonProcessingException {
    assertThat(JsonUtils.toJson(null)).isEqualTo("null");
  }

  @Test
  @DisplayName("should serialize primitive types")
  void shouldSerializePrimitiveTypes() throws JsonProcessingException {
    assertThat(JsonUtils.toJson(42)).isEqualTo("42");
    assertThat(JsonUtils.toJson(true)).isEqualTo("true");
    assertThat(JsonUtils.toJson("test")).isEqualTo("\"test\"");
    assertThat(JsonUtils.toJson(3.14159)).isEqualTo("3.14159");
  }

  @Test
  @DisplayName("should serialize arrays and collections")
  void shouldSerializeArraysAndCollections() throws JsonProcessingException {
    int[] intArray = {1, 2, 3};
    List<String> stringList = Arrays.asList("a", "b", "c");

    String intArrayJson = JsonUtils.toJson(intArray);
    String stringListJson = JsonUtils.toJson(stringList);

    assertThat(JsonUtils.fromJson(intArrayJson, int[].class))
        .containsExactly(1, 2, 3);
    assertThat(JsonUtils.fromJson(stringListJson, List.class))
        .containsExactly("a", "b", "c");
  }

  @Test
  @DisplayName("should serialize complex objects")
  void shouldSerializeComplexObjects() throws JsonProcessingException {
    LocalDateTime birthDate = LocalDateTime.of(1993, 1, 1, 0, 0);
    List<String> hobbies = Arrays.asList("reading", "gaming");
    Map<String, Object> attributes = new HashMap<>();
    attributes.put("height", 175);
    attributes.put("weight", 70);

    TestPerson originalPerson = new TestPerson(
        "John Doe",
        30,
        birthDate,
        hobbies,
        attributes
    );

    String json = JsonUtils.toJson(originalPerson);
    TestPerson deserializedPerson = JsonUtils.fromJson(json, TestPerson.class);

    assertThat(deserializedPerson)
        .usingRecursiveComparison()
        .isEqualTo(originalPerson);

    // Verify specific field serialization
    assertThat(deserializedPerson)
        .extracting(
            TestPerson::getName,
            TestPerson::getAge,
            TestPerson::getBirthDate,
            TestPerson::getHobbies
                   )
        .containsExactly(
            "John Doe",
            30,
            birthDate,
            hobbies
                        );

    assertThat(deserializedPerson.getAttributes())
        .containsEntry("height", 175)
        .containsEntry("weight", 70)
        .hasSize(2);
  }

  @Test
  @DisplayName("should handle empty collections")
  void shouldHandleEmptyCollections() throws JsonProcessingException {
    List<String> emptyList = new ArrayList<>();
    Map<String, Object> emptyMap = new HashMap<>();

    String listJson = JsonUtils.toJson(emptyList);
    String mapJson = JsonUtils.toJson(emptyMap);

    assertThat(JsonUtils.fromJson(listJson, List.class)).isEmpty();
    assertThat(JsonUtils.fromJson(mapJson, Map.class)).isEmpty();
  }

  @Test
  @DisplayName("should throw exception for circular references")
  void shouldThrowExceptionForCircularReferences() {
    CircularReference obj = new CircularReference("parent", null);
    CircularReference child = new CircularReference("child", obj);
    obj.setParent(child);

    assertThatThrownBy(() -> JsonUtils.toJson(obj))
        .isInstanceOf(JsonProcessingException.class);
  }

  @Test
  @DisplayName("should handle null with pretty printing")
  void shouldHandleNullWithPrettyPrinting() throws JsonProcessingException {
    assertThat(JsonUtils.toJson(null, true)).isEqualTo("null");
  }

  @Test
  @DisplayName("should pretty print complex objects")
  void shouldPrettyPrintComplexObjects() throws JsonProcessingException {
    LocalDateTime birthDate = LocalDateTime.of(1998, 1, 1, 0, 0);
    List<String> hobbies = Arrays.asList("swimming", "dancing");
    Map<String, Object> attributes = new HashMap<>();
    attributes.put("key1", "value1");
    attributes.put("key2", "value2");

    TestPerson person = new TestPerson(
        "Jane Doe",
        25,
        birthDate,
        hobbies,
        attributes
    );

    String json = JsonUtils.toJson(person, true);

    // Verify formatting
    assertThat(json)
        .contains("{\n")
        .contains("  \"name\"")
        .contains("  \"age\"")
        .contains("  \"birthDate\"")
        .contains("  \"hobbies\"")
        .contains("  \"attributes\"");

    // Verify data integrity with recursive comparison
    TestPerson deserializedPerson = JsonUtils.fromJson(json, TestPerson.class);
    assertThat(deserializedPerson)
        .usingRecursiveComparison()
        .isEqualTo(person);
  }

  @ParameterizedTest
  @DisplayName("should maintain data integrity with pretty printing")
  @ValueSource(booleans = {true, false})
  void shouldMaintainDataIntegrityWithPrettyPrinting(boolean pretty) throws JsonProcessingException {
    TestPerson originalPerson = new TestPerson(
        "Test Person",
        20,
        LocalDateTime.now(),
        Arrays.asList("hobby1", "hobby2"),
        new HashMap<>()
    );

    String json = JsonUtils.toJson(originalPerson, pretty);
    TestPerson deserializedPerson = JsonUtils.fromJson(json, TestPerson.class);

    assertThat(deserializedPerson)
        .usingRecursiveComparison()
        .isEqualTo(originalPerson);
  }

  @Test
  @DisplayName("should throw exception for null input in toJsonBytes")
  void shouldThrowExceptionForNullInToJsonBytes() {
    assertThatThrownBy(() -> JsonUtils.toJsonBytes(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("object cannot be null");
  }

  @Test
  @DisplayName("should convert object to UTF-8 bytes")
  void shouldConvertObjectToUtf8Bytes() throws JsonProcessingException {
    TestPerson originalPerson = new TestPerson("Test", 25, null, null, null);
    byte[] bytes = JsonUtils.toJsonBytes(originalPerson);

    TestPerson deserializedPerson = JsonUtils.fromJson(new String(bytes), TestPerson.class);
    assertThat(deserializedPerson)
        .usingRecursiveComparison()
        .isEqualTo(originalPerson);
  }

  @Test
  @DisplayName("should handle special characters in UTF-8")
  void shouldHandleSpecialCharacters() throws JsonProcessingException {
    TestPerson originalPerson = new TestPerson("Tëst×Ñåmé", 25, null, null, null);
    byte[] bytes = JsonUtils.toJsonBytes(originalPerson);

    TestPerson deserializedPerson = JsonUtils.fromJson(new String(bytes), TestPerson.class);
    assertThat(deserializedPerson)
        .usingRecursiveComparison()
        .isEqualTo(originalPerson);
  }

  @Test
  @DisplayName("should handle empty objects")
  void shouldHandleEmptyObjects() throws JsonProcessingException {
    TestPerson originalPerson = new TestPerson();
    byte[] bytes = JsonUtils.toJsonBytes(originalPerson);

    TestPerson deserializedPerson = JsonUtils.fromJson(new String(bytes), TestPerson.class);
    assertThat(deserializedPerson)
        .usingRecursiveComparison()
        .isEqualTo(originalPerson);
  }


}
