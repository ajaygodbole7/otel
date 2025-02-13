package org.observability.otel.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;

/**
 * Unit test configuration with minimal context loading.
 * Excludes database dependencies and configures JSON handling.
 */
@TestConfiguration
// Disable JPA and Database auto-configuration for unit tests
@EnableAutoConfiguration(exclude = {
    DataSourceAutoConfiguration.class,
    HibernateJpaAutoConfiguration.class,
    DataSourceTransactionManagerAutoConfiguration.class
})
public class UnitTestConfig {

  /**
   * ObjectMapper configured for test JSON serialization.
   * Includes Java 8 date/time support and flexible parsing.
   */
  @Bean
  @Primary
  public ObjectMapper objectMapper() {
    return new ObjectMapper()
        // Add support for Java 8 Date/Time types
        .registerModule(new JavaTimeModule())
        // Use ISO-8601 date/time format (e.g., "2024-01-31T15:30:00Z") instead of numeric timestamps
        .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
        // Don't fail if JSON has properties not in our Java classes
        // Useful when API response has more fields than we need
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        // Allow single values to be read as one-element arrays
        // Example: field: "value" can be read as field: ["value"]
        .configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true)
        // Auto-detect and register any other modules on the classpath
        .findAndRegisterModules();
  }

}
