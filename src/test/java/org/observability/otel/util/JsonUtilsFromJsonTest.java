package org.observability.otel.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("JsonUtils.fromJson methods")
class JsonUtilsFromJsonTest {

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  static class Person {
    private String name;
    private int age;
  }

  @Test
  @DisplayName("should deserialize valid JSON to JsonNode")
  void shouldDeserializeValidJsonToJsonNode() throws JsonProcessingException {
    String json = "{\"name\":\"Alice\",\"age\":25}";
    JsonNode node = JsonUtils.fromJson(json);
    assertThat(node).isNotNull();
    assertThat(node.get("name").asText()).isEqualTo("Alice");
    assertThat(node.get("age").asInt()).isEqualTo(25);
  }

  @Test
  @DisplayName("should throw JsonProcessingException for invalid JSON when parsing to JsonNode")
  void shouldThrowForInvalidJsonWhenParsingToJsonNode() {
    String invalidJson = "{\"name\":\"Alice\",\"age\":}";
    assertThatThrownBy(() -> JsonUtils.fromJson(invalidJson))
        .isInstanceOf(JsonProcessingException.class);
  }

  @Test
  @DisplayName("should throw IllegalArgumentException for oversized JSON")
  void shouldThrowForOversizedJson() {
    String oversized = "x".repeat(JsonUtils.MAX_JSON_LENGTH + 1);
    assertThatThrownBy(() -> JsonUtils.fromJson(oversized))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("JSON exceeds maximum allowed length");
  }

  @Test
  @DisplayName("should deserialize valid JSON to typed class")
  void shouldDeserializeValidJsonToTypedClass() throws JsonProcessingException {
    String json = "{\"name\":\"Bob\",\"age\":30}";
    Person person = JsonUtils.fromJson(json, Person.class);
    assertThat(person).isEqualTo(new Person("Bob", 30));
  }

  @Test
  @DisplayName("should throw JsonProcessingException for invalid JSON when deserializing to class")
  void shouldThrowForInvalidJsonWhenDeserializingToClass() {
    String invalidJson = "{\"name\":\"Bob\",\"age\":}";
    assertThatThrownBy(() -> JsonUtils.fromJson(invalidJson, Person.class))
        .isInstanceOf(JsonProcessingException.class);
  }

  @Test
  @DisplayName("should throw IllegalArgumentException for null target class")
  void shouldThrowForNullTargetClass() {
    String json = "{\"name\":\"Bob\",\"age\":30}";
    assertThatThrownBy(() -> JsonUtils.fromJson(json, (Class<Person>) null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Target class cannot be null");
  }

  @Test
  @DisplayName("should deserialize JSON array to typed list via TypeReference")
  void shouldDeserializeJsonArrayToTypedListViaTypeReference() throws JsonProcessingException {
    String json = "[{\"name\":\"Charlie\",\"age\":40}, {\"name\":\"Dana\",\"age\":35}]";
    List<Person> expected = List.of(new Person("Charlie", 40), new Person("Dana", 35));
    List<Person> persons = JsonUtils.fromJson(json, new TypeReference<List<Person>>() {});
    assertThat(persons).isEqualTo(expected);
  }

  @Test
  @DisplayName("should throw JsonProcessingException for invalid JSON when using TypeReference")
  void shouldThrowForInvalidJsonWithTypeReference() {
    String invalidJson = "[{\"name\":\"Charlie\",\"age\":40,}]"; // Trailing comma
    assertThatThrownBy(() -> JsonUtils.fromJson(invalidJson, new TypeReference<List<Person>>() {}))
        .isInstanceOf(JsonProcessingException.class);
  }

  @Test
  @DisplayName("should throw IllegalArgumentException for null TypeReference")
  void shouldThrowForNullTypeReference() {
    String json = "[{\"name\":\"Charlie\",\"age\":40}]";
    assertThatThrownBy(() -> JsonUtils.fromJson(json, (TypeReference<List<Person>>) null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Type reference must not be null");
  }
}
