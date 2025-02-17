package org.observability.otel.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.PathNotFoundException;
import com.jayway.jsonpath.spi.json.JsonSmartJsonProvider;
import com.jayway.jsonpath.spi.mapper.JsonSmartMappingProvider;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import net.minidev.json.JSONValue;
import net.minidev.json.parser.JSONParser;
import net.minidev.json.parser.ParseException;
import org.apache.commons.lang3.StringUtils;

/**
 * A utility class for JSON operations supporting parsing, serialization, and file operations.
 * Provides RFC 7159/4627 compliant JSON handling with:
 * <ul>
 *   <li>JSON validation and conversion</li>
 *   <li>Object to JSON serialization</li>
 *   <li>JSON to object deserialization</li>
 *   <li>File-based JSON operations</li>
 *   <li>JSONPath-based value extraction</li>
 * </ul>
 *
 * Thread-safe and null-safe operations with consistent exception handling.
 * Maximum JSON size is limited to 10MB for safety.
 */

@Slf4j
@UtilityClass
public class JsonUtils {

  static final int MAX_JSON_LENGTH = 10_000_000; // 10MB
  private static final ObjectMapper objectMapper = new ObjectMapper();
  private static final Configuration jsonPathConfig;

  static {
    // Add support for Java 8 Date/Time types
    objectMapper.registerModule(new JavaTimeModule());
    // Use ISO-8601 date/time format (e.g., "2024-01-31T15:30:00Z") instead of numeric timestamps
    objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    /*
     * Create an isolated JsonPath configuration for this utility class
     *
     * 1. Ensures JSONPath always uses json-smart to avoid inconsistencies from multiple providers.
     * 2. Standardizes mapping from JSON to Java types with json-smart’s mapping provider.
     * 3. Sets default options for JSONPath, ensuring consistent evaluation of expressions.
     */
    jsonPathConfig =
        Configuration.builder()
            .jsonProvider(new JsonSmartJsonProvider())
            .mappingProvider(new JsonSmartMappingProvider())
            .options(EnumSet.noneOf(Option.class))
            .build();
  }

  /**
   * Validates if a string is valid JSON using RFC 7159 standard. This standard allows any JSON
   * value as root element.
   *
   * @param s the string to validate
   * @return true if the string is valid JSON, false otherwise
   */
  public static boolean isJson(String s) {
    validateJsonSize(s);
    return isJson(s, false);
  }

  /**
   * Validates if a string is valid JSON with option for strict validation. - RFC 4627 (strict):
   * root must be object/array, no leading zeros in numbers - RFC 7159: allows any value as
   * root(strings/numbers/boolean/null), more lenient number formats Common to both: requires double
   * quotes, no comments, no trailing commas
   *
   * @param s the string to validate
   * @param strict if true, uses RFC 4627; if false, uses RFC 7159
   * @return true if the string is valid JSON according to specified standard
   */
  public static boolean isJson(String s, boolean strict) {
    if (StringUtils.isBlank(s)) {
      throw new IllegalArgumentException("Input String cannot be null/empty/blank");
    }
    // Trim the input to remove leading/trailing whitespace.
    String trimmed = s.trim();


    if (strict) {
      // RFC 4627 requires root to be object or array
      if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
        return false;
      }
      try {
        // For strict validation, using RFC 4627
        JSONParser parser = new JSONParser(JSONParser.MODE_RFC4627);
        parser.parse(trimmed);
        return true;
      } catch (ParseException e) {
        return false;
      }
    }

    try {
      // For lenient validation, using Jackson's ObjectMapper (RFC 7159)
      objectMapper.readTree(trimmed);
      return true;
    } catch (IOException e) {
      return false;
    }
  }

  /**
   * Converts raw JSON into a standardized strict JSON string following RFC 4627.
   * Enforces strict JSON rules: root must be object/array, no leading zeros in numbers,
   * requires double quotes, no comments, no trailing commas.
   *
   * @param raw the raw JSON string to convert
   * @return a strict JSON string conforming to RFC 4627
   * @throws JsonProcessingException if the JSON is malformed or doesn't meet RFC 4627 requirements
   * @throws IllegalArgumentException if raw is null or blank
   * @see #isJson(String, boolean)
   */
  public static String toStrictJson(String raw) throws JsonProcessingException {
    validateJsonSize(raw);
    try {
      JSONParser parser = new JSONParser(JSONParser.MODE_RFC4627);
      Object obj = parser.parse(raw);
      return objectMapper.writeValueAsString(obj);
    } catch (Exception e) {
      throw new JsonProcessingException("Failed to convert to strict JSON", e) {};
    }
  }

  /**
   Serializes an object into its JSON string representation.
   * Uses json-smart's JSONValue serialization for performance, with Jackson as fallback.
   * Null objects are serialized as "null" string.
   *
   * @param o the object to serialize
   * @return the JSON string representation of the object
   * @throws JsonProcessingException if object cannot be serialized to JSON
   * @see #toJson(Object, boolean) for pretty-printing options
   */
  public static String toJson(Object o) throws JsonProcessingException {
    return toJson(o, false);
  }

  /**
   * Serializes an object into JSON with optional pretty printing.
   * Uses json-smart for non-pretty printing (faster), Jackson for pretty printing.
   * Null objects are serialized as "null" string.
   *
   * @param o the object to serialize
   * @param pretty if true, formats JSON with indentation and line breaks
   * @return the JSON string representation of the object
   * @throws JsonProcessingException if object cannot be serialized to JSON
   */
  public static String toJson(Object o, boolean pretty) throws JsonProcessingException {
    if (o == null) {
      return "null";
    }

    if (!pretty) {
      try {
        return JSONValue.toJSONString(o);
      } catch (Throwable t) {
        log.warn("Fast serialization failed: {}. Falling back to Jackson.", t.getMessage());
      }
    }

    return pretty
        ? objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(o)
        : objectMapper.writeValueAsString(o);
  }

  /**
   * Serializes an object into a UTF-8 encoded JSON byte array.
   * The resulting bytes represent the JSON string in UTF-8 encoding.
   *
   * @param o the object to serialize
   * @return UTF-8 encoded bytes of the JSON representation
   * @throws JsonProcessingException if object cannot be serialized to JSON
   * @throws IllegalArgumentException if the object is null
   */
  public static byte[] toJsonBytes(Object o) throws JsonProcessingException {
    if (o == null) {
      throw new IllegalArgumentException("object cannot be null");
    }
    return toJson(o).getBytes(StandardCharsets.UTF_8);
  }

  /**
   * Parses a JSON string into a JsonNode tree representation.
   * The resulting JsonNode can be traversed to access JSON structure.
   *
   * @param json the JSON string to parse
   * @return JsonNode representing the JSON structure
   * @throws JsonProcessingException if JSON is invalid or cannot be parsed
   * @throws IllegalArgumentException if json is null, blank, or exceeds size limit
   */
  public static JsonNode fromJson(String json) throws JsonProcessingException {
    validateJsonSize(json);
    return objectMapper.readTree(json);
  }

  /**
   * Parses a JSON string into a specified Java type.
   * The JSON structure must match the target class's properties.
   *
   * @param json the JSON string to parse
   * @param clazz the class to deserialize into
   * @param <T> the type of object to return
   * @return an instance of the specified class populated from JSON
   * @throws JsonProcessingException if JSON cannot be parsed or mapped to class
   * @throws IllegalArgumentException if json is invalid or clazz is null
   */
  public static <T> T fromJson(String json, Class<T> clazz) throws JsonProcessingException {
    validateJsonSize(json);
    if (clazz == null) {
      throw new IllegalArgumentException("Target class cannot be null");
    }
    return objectMapper.readValue(json, clazz);
  }

  /**
   Parses a JSON string into an object of a complex type (e.g., List<CustomType>).
   * Uses TypeReference to preserve generic type information during deserialization.
   *
   * @param json    the JSON string to parse
   * @param typeRef the type reference describing the target type
   * @param <T>     the type to convert the JSON to
   * @return an instance of type T containing the JSON data
   * @throws JsonProcessingException if JSON cannot be parsed or mapped to type T
   * @throws IllegalArgumentException if json is blank/null/oversized or typeRef is null
   */
  public static <T> T fromJson(String json, TypeReference<T> typeRef)
      throws JsonProcessingException {
    validateJsonSize(json);
    if (typeRef == null) {
      throw new IllegalArgumentException("Type reference must not be null");
    }
    return objectMapper.readValue(json, typeRef);
  }

  /**
   * Extracts an optional value from a JSON string using a JSONPath expression.
   *
   * @param json the JSON string
   * @param jsonPathExpression the JSONPath expression to evaluate
   * @param <T> the expected type of the result
   * @return an Optional containing the extracted value, or empty if not found
   * @throws JsonProcessingException if JSON processing fails
   */
  public static <T> Optional<T> extractValue(String json, String jsonPathExpression)
      throws JsonProcessingException {
    validateJsonSize(json);
    notBlank(jsonPathExpression, "JSONPath expression must not be null");

    try {
      // Parse JSON using our configured JsonPath instance
      // then extract value using the provided path expression
      T value = JsonPath
          .using(jsonPathConfig)
          .parse(json)
          .read(jsonPathExpression);

      // Wrap the result in Optional to handle null values from Parsing
      return Optional.ofNullable(value);
    } catch (PathNotFoundException e) {
      // Return empty Optional if path doesn't exist in the JSON
      return Optional.empty();
    }
  }

  /**
   * Reads and parses a JSON file into an object of the specified type.
   * Validates file existence, accessibility, and JSON content.
   *
   * @param filePath   the path to the JSON file to read
   * @param valueType  the class of the target type
   * @param <T>        the type to convert the JSON to
   * @return an instance of type T containing the file's JSON data
   * @throws FileNotFoundException if the file doesn't exist
   * @throws IOException if the file cannot be read or contains invalid JSON
   * @throws IllegalArgumentException if filePath is blank or valueType is null
   * @throws SecurityException if read access is denied
   */
  public static <T> T readJsonFromFile(String filePath, Class<T> valueType) throws IOException {
    notBlank(filePath, "File path must not be null");
    if (valueType == null) {
      throw new IllegalArgumentException("Target Class must not be null");
    }
    File file = validateAndGetFile(filePath);
    try {
      return objectMapper.readValue(new File(filePath), valueType);
    } catch (JsonProcessingException e) {
      log.error("Failed to serialize object to JSON for file: {}", file.getAbsolutePath(), e);
      throw e;
    }
  }

  /**
   * Reads a JSON file and converts it to a complex type.
   *
   * @param filePath the path to the JSON file
   * @param typeRef the type reference for complex generic types
   * @param <T> the type of object to deserialize the JSON into, must have a no-args constructor
   * @return the converted object
   * @throws IOException if reading fails or JSON is invalid
   */
  public static <T> T readJsonFromFile(String filePath, TypeReference<T> typeRef)
      throws IOException {
    notBlank(filePath, "File path must not be null");
    if (typeRef == null) {
      throw new IllegalArgumentException("Type reference must not be null");
    }
    File file = validateAndGetFile(filePath);
    try {
      return objectMapper.readValue(file, typeRef);
    } catch (JsonProcessingException e) {
      throw new IOException("Failed to parse JSON from file: " + filePath, e);
    }
  }

  /**
   * Writes an object as JSON to a file with optional pretty printing. Creates parent directories if
   * they don't exist.
   *
   * @param obj the object to write
   * @param filePath the path to write the JSON to
   * @param pretty whether to pretty print the JSON
   * @throws IllegalArgumentException if the file path is blank or has no parent directory
   * @throws JsonProcessingException if the object cannot be serialized to JSON
   * @throws IOException if directory creation fails or writing to file fails
   */
  public static void writeJsonToFile(Object obj, String filePath, boolean pretty)
      throws JsonProcessingException, IOException {

    notBlank(filePath, "File path must not be null");
    if (obj == null) {
      throw new IllegalArgumentException("Parameter obj  to write must not be null");
    }

    File file = new File(filePath).getAbsoluteFile();
    String absolutePath = file.getAbsolutePath();

    File parent =
        Optional.ofNullable(file.getParentFile())
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "Invalid file path, no parent directory: " + absolutePath));

    if (!parent.exists() && !parent.mkdirs()) {
      throw new IOException("Failed to create directory: " + parent.getAbsolutePath());
    }

    try {
      (pretty ? objectMapper.writerWithDefaultPrettyPrinter() : objectMapper.writer())
          .writeValue(file, obj);
    } catch (JsonProcessingException e) {
      log.error("Failed to serialize object to JSON for file: {}", absolutePath, e);
      throw e;
    } catch (IOException e) {
      log.error("Failed to write to file: {}", absolutePath, e);
      throw e;
    }
  }

  /**
   * Writes an object as pretty-printed JSON to a file.
   * Convenience method that calls writeJsonToFile with pretty=true.
   *
   * @param obj      the object to serialize to JSON
   * @param filePath the path where the JSON file should be written
   * @throws IOException if the file cannot be written
   * @throws IllegalArgumentException if obj is null or filePath is blank
   * @throws SecurityException if write access is denied
   * @see #writeJsonToFile(Object, String, boolean)
   */
  public static void writePrettyJsonToFile(Object obj, String filePath) throws IOException {
    writeJsonToFile(obj, filePath, true);
  }

  /**
   * Validates that the specified character sequence is neither null, empty, nor consisting only of
   * whitespace characters. Uses Apache Commons StringUtils.isBlank for validation.
   *
   * @param <T> the character sequence type (String, StringBuilder, etc.)
   * @param chars the character sequence to check
   * @param message the error message if invalid
   * @throws IllegalArgumentException if chars is null, empty or whitespace-only or if message is
   *     null or blank
   */
  public static <T extends CharSequence> T notBlank(T chars, String message) {

    if (StringUtils.isBlank(message)) {
      throw new IllegalArgumentException("Message must not be null or blank");
    }

    if (StringUtils.isBlank(chars)) {
      throw new IllegalArgumentException(message);
    }

    return chars;
  }

  /**
   * Validates that the JSON string is not blank and doesn't exceed maximum length.
   * Maximum length is defined by MAX_JSON_LENGTH constant.
   *
   * @param json the JSON string to validate
   * @throws IllegalArgumentException if json is blank or exceeds maximum length
   */
  private static void validateJsonSize(String json) {

    notBlank(json, "json must not be null or blank");

    if (json.length() > MAX_JSON_LENGTH) {
      throw new IllegalArgumentException(
          "JSON exceeds maximum allowed length of " + MAX_JSON_LENGTH + " characters");
    }
  }

  private static File validateAndGetFile(String filePath) throws IOException {
    notBlank(filePath, "File path must not be null");
    File file = new File(filePath).getAbsoluteFile();
    if (!file.exists()) {
      throw new FileNotFoundException("File not found: " + file.getAbsolutePath());
    }
    if (!file.isFile()) {
      throw new IOException("Path is not a file: " + file.getAbsolutePath());
    }
    if (!file.canRead()) {
      throw new IOException("Cannot read file: " + file.getAbsolutePath());
    }
    return file;
  }
}
