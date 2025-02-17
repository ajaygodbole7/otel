package org.observability.otel.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;

class JsonUtilsFromJsonTest {

  // A simple POJO used for testing deserialization.
  static class Person {
    public String name;
    public int age;

    // No-args constructor required for Jackson
    public Person() {}

    public Person(String name, int age) {
      this.name = name;
      this.age = age;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      Person person = (Person) o;
      return age == person.age && name.equals(person.name);
    }

    @Override
    public int hashCode() {
      return java.util.Objects.hash(name, age);
    }
  }

  @Test
  void testFromJsonToJsonNode_Valid() throws JsonProcessingException {
    String json = "{\"name\":\"Alice\",\"age\":25}";
    JsonNode node = JsonUtils.fromJson(json);
    assertThat(node).isNotNull();
    assertThat(node.get("name").asText()).isEqualTo("Alice");
    assertThat(node.get("age").asInt()).isEqualTo(25);
  }

  @Test
  void testFromJsonToJsonNode_Invalid() {
    String invalidJson = "{\"name\":\"Alice\",\"age\":}";
    assertThatThrownBy(() -> JsonUtils.fromJson(invalidJson))
        .isInstanceOf(JsonProcessingException.class);
  }

  @Test
  void testFromJsonToJsonNode_Oversized() {
    String oversized = "x".repeat(JsonUtils.MAX_JSON_LENGTH + 1);
    assertThatThrownBy(() -> JsonUtils.fromJson(oversized))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("JSON exceeds maximum allowed length");
  }

  @Test
  void testFromJsonWithClass_Valid() throws JsonProcessingException {
    String json = "{\"name\":\"Bob\",\"age\":30}";
    Person expected = new Person("Bob", 30);
    Person person = JsonUtils.fromJson(json, Person.class);
    assertThat(person).isEqualTo(expected);
  }

  @Test
  void testFromJsonWithClass_InvalidJson() {
    String invalidJson = "{\"name\":\"Bob\",\"age\":}";
    assertThatThrownBy(() -> JsonUtils.fromJson(invalidJson, Person.class))
        .isInstanceOf(JsonProcessingException.class);
  }

  @Test
  void testFromJsonWithClass_NullType() {
    String json = "{\"name\":\"Bob\",\"age\":30}";
    assertThatThrownBy(() -> JsonUtils.fromJson(json, (Class<Person>) null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Target class cannot be null");
  }

  @Test
  void testFromJsonWithTypeReference_Valid() throws JsonProcessingException {
    String json = "[{\"name\":\"Charlie\",\"age\":40}, {\"name\":\"Dana\",\"age\":35}]";
    List<Person> expected = List.of(new Person("Charlie", 40), new Person("Dana", 35));
    List<Person> persons = JsonUtils.fromJson(json, new TypeReference<List<Person>>() {});
    assertThat(persons).isEqualTo(expected);
  }

  @Test
  void testFromJsonWithTypeReference_InvalidJson() {
    String invalidJson = "[{\"name\":\"Charlie\",\"age\":40,}]"; // Trailing comma
    assertThatThrownBy(() -> JsonUtils.fromJson(invalidJson, new TypeReference<List<Person>>() {}))
        .isInstanceOf(JsonProcessingException.class);
  }

  @Test
  void testFromJsonWithTypeReference_NullType() {
    String json = "[{\"name\":\"Charlie\",\"age\":40}]";
    assertThatThrownBy(() -> JsonUtils.fromJson(json, (TypeReference<List<Person>>) null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Type reference must not be null");
  }
}
