package org.observability.otel.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("JsonUtils File Operations")
class JsonUtilsFileTest {

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

  @TempDir
  Path tempDir;

  // readJsonFromFile(String filePath, Class<T> valueType) tests

  @Test
  @DisplayName("should read simple object from file successfully")
  void shouldReadSimpleObjectFromFile() throws IOException {
    // Arrange
    TestPerson person = new TestPerson("John", 30, LocalDateTime.now(), null, null);
    Path jsonFile = tempDir.resolve("person.json");
    Files.writeString(jsonFile, JsonUtils.toJson(person));

    // Act
    TestPerson readPerson = JsonUtils.readJsonFromFile(jsonFile.toString(), TestPerson.class);

    // Assert
    assertThat(readPerson)
        .usingRecursiveComparison()
        .isEqualTo(person);
  }

  @Test
  @DisplayName("should throw FileNotFoundException when file doesn't exist")
  void shouldThrowFileNotFoundForNonExistentFile() {
    String nonExistentFile = tempDir.resolve("nonexistent.json").toString();

    assertThatThrownBy(() -> JsonUtils.readJsonFromFile(nonExistentFile, TestPerson.class))
        .isInstanceOf(FileNotFoundException.class)
        .hasMessageContaining("File not found");
  }

  @Test
  @DisplayName("should throw IllegalArgumentException for null file path")
  void shouldThrowIllegalArgumentForNullPath() {
    assertThatThrownBy(() -> JsonUtils.readJsonFromFile(null, TestPerson.class))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("File path must not be null");
  }

  @Test
  @DisplayName("should throw IllegalArgumentException for null class type")
  void shouldThrowIllegalArgumentForNullClass() {
    Class<TestPerson> nullClass = null;
    assertThatThrownBy(() -> JsonUtils.readJsonFromFile("test.json", nullClass))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Target Class must not be null");
  }

  @Test
  @DisplayName("should throw JsonProcessingException for invalid JSON content")
  void shouldThrowJsonProcessingForInvalidContent() throws IOException {
    Path jsonFile = tempDir.resolve("invalid.json");
    Files.writeString(jsonFile, "{ invalid json }");

    assertThatThrownBy(() -> JsonUtils.readJsonFromFile(jsonFile.toString(), TestPerson.class))
        .isInstanceOf(JsonProcessingException.class);
  }

  // readJsonFromFile(String filePath, TypeReference<T> typeRef) tests

  @Test
  @DisplayName("should read complex type from file successfully")
  void shouldReadComplexTypeFromFile() throws IOException {
    // Arrange
    List<TestPerson> people = Arrays.asList(
        new TestPerson("John", 30, LocalDateTime.now(), null, null),
        new TestPerson("Jane", 25, LocalDateTime.now(), null, null)
                                           );
    Path jsonFile = tempDir.resolve("people.json");
    Files.writeString(jsonFile, JsonUtils.toJson(people));

    // Act
    List<TestPerson> readPeople = JsonUtils.readJsonFromFile(
        jsonFile.toString(),
        new TypeReference<List<TestPerson>>() {}
                                                            );

    // Assert
    assertThat(readPeople)
        .usingRecursiveComparison()
        .isEqualTo(people);
  }

  @Test
  @DisplayName("should throw IllegalArgumentException for null TypeReference")
  void shouldThrowIllegalArgumentForNullTypeRef() {
    assertThatThrownBy(() -> JsonUtils.readJsonFromFile("test.json", (TypeReference<List<TestPerson>>) null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Type reference must not be null");
  }

  // writeJsonToFile tests

  @Test
  @DisplayName("should write object to file with pretty printing")
  void shouldWriteObjectWithPrettyPrinting() throws IOException {
    // Arrange
    TestPerson person = new TestPerson("John", 30, LocalDateTime.now(), null, null);
    Path jsonFile = tempDir.resolve("output.json");

    // Act
    JsonUtils.writeJsonToFile(person, jsonFile.toString(), true);

    // Assert
    String content = Files.readString(jsonFile);
    assertThat(content)
        .contains("{\n")  // Check pretty printing
        .contains("  \"") // Check indentation
        .contains("\"name\"")
        .contains("\"John\"");

    // Verify data integrity
    TestPerson readPerson = JsonUtils.readJsonFromFile(jsonFile.toString(), TestPerson.class);
    assertThat(readPerson)
        .usingRecursiveComparison()
        .isEqualTo(person);
  }

  @Test
  @DisplayName("should write object to file without pretty printing")
  void shouldWriteObjectWithoutPrettyPrinting() throws IOException {
    // Arrange
    TestPerson person = new TestPerson("John", 30, LocalDateTime.now(), null, null);
    Path jsonFile = tempDir.resolve("output.json");

    // Act
    JsonUtils.writeJsonToFile(person, jsonFile.toString(), false);

    // Assert
    String content = Files.readString(jsonFile);
    assertThat(content)
        .doesNotContain("{\n")  // Check no pretty printing
        .contains("\"name\"")
        .contains("\"John\"");

    // Verify data integrity
    TestPerson readPerson = JsonUtils.readJsonFromFile(jsonFile.toString(), TestPerson.class);
    assertThat(readPerson)
        .usingRecursiveComparison()
        .isEqualTo(person);
  }

  @Test
  @DisplayName("should throw IllegalArgumentException for null object")
  void shouldThrowIllegalArgumentForNullObject() {
    assertThatThrownBy(() -> JsonUtils.writeJsonToFile(null, "test.json", false))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("should throw IOException when directory creation fails")
  void shouldThrowIOExceptionForDirectoryCreationFailure() {
    TestPerson person = new TestPerson("John", 30, LocalDateTime.now(), null, null);
    String invalidPath = "/nonexistent/directory/file.json";

    assertThatThrownBy(() -> JsonUtils.writeJsonToFile(person, invalidPath, false))
        .isInstanceOf(IOException.class)
        .hasMessageContaining("Failed to create directory");
  }

  @Test
  @DisplayName("should throw IllegalArgumentException for invalid file path")
  void shouldThrowIllegalArgumentForInvalidPath() {
    TestPerson person = new TestPerson("John", 30, LocalDateTime.now(), null, null);

    assertThatThrownBy(() -> JsonUtils.writeJsonToFile(person, "", false))
        .isInstanceOf(IllegalArgumentException.class);
  }

  // writePrettyJsonToFile tests

  @Test
  @DisplayName("should write pretty-printed JSON to file")
  void shouldWritePrettyPrintedJson() throws IOException {
    // Arrange
    TestPerson person = new TestPerson("John", 30, LocalDateTime.now(), null, null);
    Path jsonFile = tempDir.resolve("pretty.json");

    // Act
    JsonUtils.writePrettyJsonToFile(person, jsonFile.toString());

    // Assert
    String content = Files.readString(jsonFile);
    assertThat(content)
        .contains("{\n")  // Check pretty printing
        .contains("  \"") // Check indentation
        .contains("\"name\"")
        .contains("\"John\"");

    // Verify data integrity
    TestPerson readPerson = JsonUtils.readJsonFromFile(jsonFile.toString(), TestPerson.class);
    assertThat(readPerson)
        .usingRecursiveComparison()
        .isEqualTo(person);
  }

  @Test
  @DisplayName("should handle permission denied errors")
  void shouldHandlePermissionDeniedErrors() throws IOException {
    // Create a read-only directory
    Path readOnlyDir = tempDir.resolve("readonly");
    Files.createDirectory(readOnlyDir);
    File readOnlyFile = readOnlyDir.toFile();

    // Make directory read-only
    readOnlyFile.setReadOnly();

    Path targetFile = readOnlyDir.resolve("secured.json");
    TestPerson person = new TestPerson("John", 30, LocalDateTime.now(), null, null);

    try {
      assertThatThrownBy(() -> JsonUtils.writePrettyJsonToFile(person, targetFile.toString()))
          .isInstanceOf(FileNotFoundException.class)
          .hasMessageContaining("Permission denied");
    } finally {
      // Restore permissions to allow cleanup
      readOnlyFile.setWritable(true);
    }
  }
}
