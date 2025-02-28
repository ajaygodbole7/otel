package org.observability.otel.util;

import io.hypersistence.tsid.TSID;
import net.datafaker.Faker;

import java.sql.*;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Comprehensive demo of Time Sorted Identifiers (TSIDs) compared to UUIDs
 * with PostgreSQL database performance testing
 *
 * Dependencies:
 * - io.hypersistence:hypersistence-tsid:2.0.0
 * - com.github.f4b6a3:uuid-creator:5.3.2 (optional, for UUID v7)
 * - org.postgresql:postgresql:42.6.0
 */
public class TSIDDemo {

  private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

  public static void main(String[] args) {
    System.out.println("=== TSID vs UUID Comparison Demo ===\n");

    // Basic TSID examples
    basicTSIDExamples();

    // UUID examples
    basicUUIDExamples();

    // TSID creation performance benchmark
    benchmarkCreation();

    // Database performance testing
    databasePerformanceTest();

    // Sort order demonstration
    demonstrateSortOrder();

    // String representation comparison
    compareStringRepresentation();
  }

  /**
   * Demonstrates basic TSID operations and features
   */
  private static void basicTSIDExamples() {
    System.out.println("\n=== Basic TSID Examples ===");

    // Create a TSID using the default factory
    TSID tsid1 = TSID.fast();
    long tsid1Long = tsid1.toLong(); // Get the long value for database storage

    System.out.println("TSID (default):");
    System.out.println("- Long value (for DB primary key): " + tsid1Long);
    System.out.println("- String representation: " + tsid1);

    // Demonstrate time extraction
    Instant creationTime = tsid1.getInstant();
    LocalDateTime dateTime = LocalDateTime.ofInstant(creationTime, ZoneId.systemDefault());
    System.out.println("Creation time: " + FORMATTER.format(dateTime));

    // Create a TSID with a custom node identifier
    TSID.Factory factory = TSID.Factory.builder()
            .withNode(42)
            .build();
    TSID tsid2 = factory.generate();
    long tsid2Long = tsid2.toLong(); // Get the long value for database storage

    System.out.println("\nTSID (with node 42):");
    System.out.println("- Long value (for DB primary key): " + tsid2Long);
    System.out.println("- String representation: " + tsid2);

    // Parse a TSID from string - use a valid TSID string
    String tsidString = tsid1.toString();
    System.out.println("\nParsing from string: " + tsidString);
    TSID parsedTsid = TSID.from(tsidString);
    long parsedTsidLong = parsedTsid.toLong(); // Get the long value for database storage
    System.out.println("- Parsed TSID long value: " + parsedTsidLong);
    System.out.println("- Parsed TSID string: " + parsedTsid);

    // Create TSIDs from different nodes (simulating distributed system)
    System.out.println("\nSequential TSIDs from different nodes (simulating distributed system):");
    List<TSID> nodeSpecificIds = new ArrayList<>();

    // Create factories for different nodes
    for (int node = 1; node <= 5; node++) {
      TSID.Factory nodeFactory = TSID.Factory.builder()
              .withNode(node)  // Each node gets a unique ID (1-1023)
              .build();

      nodeSpecificIds.add(nodeFactory.generate());
    }

    for (int i = 0; i < nodeSpecificIds.size(); i++) {
      TSID id = nodeSpecificIds.get(i);
      long idLong = id.toLong(); // Get the long value for database storage
      System.out.printf("Node %d: Long value: %d, String: %s (time: %s)%n",
              i + 1,
              idLong,
              id,
              id.getInstant());
    }

    // Demonstrate sequential TSIDs from same node
    System.out.println("\nSequential TSIDs from same node (time-ordered):");
    TSID.Factory singleNodeFactory = TSID.Factory.builder()
            .withNode(1)
            .build();

    List<TSID> timeOrderedIds = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      timeOrderedIds.add(singleNodeFactory.generate());

      // Small delay to ensure time difference
      try {
        Thread.sleep(10);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }

    for (int i = 0; i < timeOrderedIds.size(); i++) {
      TSID id = timeOrderedIds.get(i);
      long idLong = id.toLong(); // Get the long value for database storage
      System.out.printf("%d. Long value: %d, String: %s (time: %s)%n",
              i + 1,
              idLong,
              id,
              FORMATTER.format(LocalDateTime.ofInstant(id.getInstant(), ZoneId.systemDefault())));
    }
  }

  /**
   * Demonstrates UUID operations for comparison
   */
  private static void basicUUIDExamples() {
    System.out.println("\n=== UUID Examples ===");

    // UUID v4 (random)
    UUID uuidV4 = UUID.randomUUID();
    System.out.println("UUID v4 (random): " + uuidV4);
    System.out.println("UUID bytes: 16 bytes (128 bits)");
    System.out.println("UUID string length: " + uuidV4.toString().length() + " characters");
    System.out.println("Version: " + uuidV4.version());

    // UUID v1 (time-based)
    UUID uuidV1 = com.github.f4b6a3.uuid.UuidCreator.getTimeBased();
    System.out.println("\nUUID v1 (time-based): " + uuidV1);
    System.out.println("Version: " + uuidV1.version());

    // For demo purposes, we can use UUID's timestamp method
    System.out.println("The UUID contains a timestamp that can be extracted");
    System.out.println("UUID v1 uses a different epoch date (1582-10-15)");

    // Time can be extracted with the standard timestamp() method
    if (uuidV1.version() == 1) {
      System.out.println("This UUID has a timestamp embedded in it");
    }

    // UUID v6 (ordered time-based)
    UUID uuidV6 = com.github.f4b6a3.uuid.UuidCreator.getTimeOrdered();
    System.out.println("\nUUID v6 (ordered time-based): " + uuidV6);
    System.out.println("Version: " + uuidV6.version());
    System.out.println("UUID v6 rearranges the timestamp to be sortable");
    System.out.println("This means UUIDs will sort in time order when sorted alphabetically");

    // UUID v7 (Unix epoch time-based)
    UUID uuidV7 = com.github.f4b6a3.uuid.UuidCreator.getTimeOrderedEpoch();
    System.out.println("\nUUID v7 (Unix epoch time-based): " + uuidV7);
    System.out.println("Version: " + uuidV7.version());
    System.out.println("UUID v7 uses the standard Unix epoch (1970-01-01)");
    System.out.println("The first part of the UUID contains the timestamp");
    System.out.println("This makes it sortable while using a familiar epoch");

    // Compare with TSID structure
    System.out.println("\nComparing with TSID structure:");
    TSID tsid = TSID.fast();
    System.out.println("TSID as long (primary value for database storage): " + tsid.toLong());
    System.out.println("TSID as string (base32 representation): " + tsid);
    System.out.println("TSID size: 8 bytes (64 bits)");
    System.out.println("TSID timestamp: " + tsid.getInstant());
    System.out.println("TSID bit layout (conceptual):");
    System.out.println("- First 48 bits: Unix timestamp (milliseconds)");
    System.out.println("- Last 16 bits: Random or node ID");
    System.out.println("- No version bits needed (unlike UUIDs)");
    System.out.println("- Total: 64 bits (8 bytes) vs 128 bits (16 bytes) for UUIDs");
  }



  /**
   * Benchmarks the creation performance of different ID types
   */
  private static void benchmarkCreation() {
    System.out.println("\n=== ID Creation Performance Benchmark ===");

    int iterations = 1_000_000;

    // Warm up
    for (int i = 0; i < 10000; i++) {
      TSID.fast();
      UUID.randomUUID();
    }

    // TSID benchmark
    long startTime = System.nanoTime();
    for (int i = 0; i < iterations; i++) {
      TSID tsid = TSID.fast();
      long tsidLong = tsid.toLong(); // This is what we'd store in the database
    }
    long tsidTime = System.nanoTime() - startTime;

    // UUID benchmark
    startTime = System.nanoTime();
    for (int i = 0; i < iterations; i++) {
      UUID uuid = UUID.randomUUID();
      // UUIDs would be stored as-is in the database (16 bytes)
    }
    long uuidTime = System.nanoTime() - startTime;

    System.out.printf("TSID creation: %,d ns (%,d ns per op)%n",
            tsidTime, tsidTime / iterations);
    System.out.printf("UUID creation: %,d ns (%,d ns per op)%n",
            uuidTime, uuidTime / iterations);
    System.out.printf("TSID is %.2fx faster than UUID for generation%n",
            (double) uuidTime / tsidTime);

    // Output reminder about storage format
    System.out.println("\nStorage format comparison:");
    System.out.println("- TSID: 8 bytes (BIGINT) when stored as long value");
    System.out.println("- UUID: 16 bytes (UUID) when stored natively");
    System.out.println("TSIDs use 50% less storage space than UUIDs");
  }

  /**
   * Tests database performance with PostgreSQL
   */
  private static void databasePerformanceTest() {
    System.out.println("\n=== PostgreSQL Database Performance Test ===");

    // Database connection parameters
    String jdbcUrl = "jdbc:postgresql://localhost:5432/demodb";
    String username = "demouser";
    String password = "demopass";

    // Number of records to insert
    int recordCount = 10_000;

    // Create a Faker instance for generating realistic test data
    Faker faker = new Faker();

    Connection conn = null;

    try {
      // Connect to the database
      System.out.println("Connecting to PostgreSQL...");
      conn = DriverManager.getConnection(jdbcUrl, username, password);
      conn.setAutoCommit(false);

      // Create test tables
      System.out.println("Creating test tables...");
      try (Statement stmt = conn.createStatement()) {
        // Create UUID table
        stmt.execute(
                "CREATE TABLE IF NOT EXISTS users_uuid (" +
                        "   id UUID PRIMARY KEY," +  // 16 bytes
                        "   first_name VARCHAR(50) NOT NULL," +
                        "   last_name VARCHAR(50) NOT NULL," +
                        "   email VARCHAR(100)," +
                        "   address VARCHAR(200)," +
                        "   phone VARCHAR(20)" +
                        ")"
        );

        // Create TSID table (using BIGINT)
        stmt.execute(
                "CREATE TABLE IF NOT EXISTS users_tsid (" +
                        "   id BIGINT PRIMARY KEY," +  // 8 bytes - this is where we store the TSID.toLong() value
                        "   first_name VARCHAR(50) NOT NULL," +
                        "   last_name VARCHAR(50) NOT NULL," +
                        "   email VARCHAR(100)," +
                        "   address VARCHAR(200)," +
                        "   phone VARCHAR(20)" +
                        ")"
        );

        // Truncate tables to ensure they're empty
        stmt.execute("TRUNCATE TABLE users_uuid");
        stmt.execute("TRUNCATE TABLE users_tsid");
        conn.commit();
      }

      // UUID inserts
      System.out.println("Inserting " + recordCount + " UUID records...");
      long uuidStart = System.currentTimeMillis();

      try (PreparedStatement pstmt = conn.prepareStatement(
              "INSERT INTO users_uuid (id, first_name, last_name, email, address, phone) " +
                      "VALUES (?, ?, ?, ?, ?, ?)")) {

        for (int i = 0; i < recordCount; i++) {
          UUID id = UUID.randomUUID();  // 16 bytes

          // Generate realistic user data with DataFaker
          String firstName = faker.name().firstName();
          String lastName = faker.name().lastName();
          String email = faker.internet().emailAddress(firstName.toLowerCase() + "." + lastName.toLowerCase());
          String address = faker.address().fullAddress();
          String phone = faker.phoneNumber().phoneNumber();

          pstmt.setObject(1, id);  // UUID as native type (16 bytes)
          pstmt.setString(2, firstName);
          pstmt.setString(3, lastName);
          pstmt.setString(4, email);
          pstmt.setString(5, address);
          pstmt.setString(6, phone);
          pstmt.addBatch();

          if (i % 1000 == 0 && i > 0) {
            pstmt.executeBatch();
          }
        }
        pstmt.executeBatch();
        conn.commit();
      }

      long uuidTime = System.currentTimeMillis() - uuidStart;

      // TSID inserts
      System.out.println("Inserting " + recordCount + " TSID records (as BIGINT)...");
      long tsidStart = System.currentTimeMillis();

      try (PreparedStatement pstmt = conn.prepareStatement(
              "INSERT INTO users_tsid (id, first_name, last_name, email, address, phone) " +
                      "VALUES (?, ?, ?, ?, ?, ?)")) {

        for (int i = 0; i < recordCount; i++) {
          TSID tsid = TSID.fast();
          long id = tsid.toLong();  // Get the long value (8 bytes) - primary key for database

          // Generate realistic user data with DataFaker
          String firstName = faker.name().firstName();
          String lastName = faker.name().lastName();
          String email = faker.internet().emailAddress(firstName.toLowerCase() + "." + lastName.toLowerCase());
          String address = faker.address().fullAddress();
          String phone = faker.phoneNumber().phoneNumber();

          pstmt.setLong(1, id);  // Store as BIGINT (8 bytes) - half the size of UUID
          pstmt.setString(2, firstName);
          pstmt.setString(3, lastName);
          pstmt.setString(4, email);
          pstmt.setString(5, address);
          pstmt.setString(6, phone);
          pstmt.addBatch();

          if (i % 1000 == 0 && i > 0) {
            pstmt.executeBatch();
          }
        }
        pstmt.executeBatch();
        conn.commit();
      }

      long tsidTime = System.currentTimeMillis() - tsidStart;

      // Report results
      System.out.println("\n--- Performance Results ---");
      System.out.printf("UUID inserts: %,d ms%n", uuidTime);
      System.out.printf("TSID inserts (as BIGINT): %,d ms%n", tsidTime);

      if (uuidTime > 0 && tsidTime > 0) {
        System.out.printf("TSID is %.2fx faster for inserts%n", (double) uuidTime / tsidTime);
      }

      // Check table sizes
      System.out.println("\n--- Table Sizes ---");
      try (Statement stmt = conn.createStatement();
           ResultSet rs = stmt.executeQuery(
                   "SELECT " +
                           "   table_name, " +
                           "   pg_size_pretty(pg_relation_size(quote_ident(table_name))) AS table_size, " +
                           "   pg_size_pretty(pg_indexes_size(quote_ident(table_name))) AS index_size " +
                           "FROM (" +
                           "   SELECT 'users_uuid' AS table_name " +
                           "   UNION ALL " +
                           "   SELECT 'users_tsid' AS table_name" +
                           ") AS tables")) {

        while (rs.next()) {
          System.out.printf("Table: %-10s Data: %-8s Index: %-8s%n",
                  rs.getString("table_name"),
                  rs.getString("table_size"),
                  rs.getString("index_size"));
        }
      }

      // Truncate tables
      System.out.println("\nTruncating tables...");
      try (Statement stmt = conn.createStatement()) {
        stmt.execute("TRUNCATE TABLE users_uuid");
        stmt.execute("TRUNCATE TABLE users_tsid");
        conn.commit();
      }

    } catch (SQLException e) {
      System.out.println("Database error: " + e.getMessage());
    } finally {
      if (conn != null) {
        try {
          conn.close();
        } catch (SQLException e) {
          // Ignore
        }
      }
    }
  }

  /**
   * Demonstrates natural sort order of different ID types
   */
  private static void demonstrateSortOrder() {
    System.out.println("\n=== ID Sort Order Demonstration ===");

    // Create IDs with a delay between them
    List<TSID> tsids = new ArrayList<>();
    List<Long> tsidLongs = new ArrayList<>(); // Store the long values
    List<UUID> uuids = new ArrayList<>();

    System.out.println("Creating IDs with time intervals...");
    for (int i = 0; i < 5; i++) {
      TSID tsid = TSID.fast();
      tsids.add(tsid);
      tsidLongs.add(tsid.toLong()); // Store the long value
      uuids.add(UUID.randomUUID());

      try {
        Thread.sleep(500); // 500ms delay
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }

    // Show TSID natural time order
    System.out.println("\nTSID creation order:");
    for (int i = 0; i < tsids.size(); i++) {
      TSID tsid = tsids.get(i);
      System.out.printf("%d. TSID long: %d, string: %s (time: %s)%n",
              i + 1,
              tsid.toLong(),
              tsid,
              FORMATTER.format(LocalDateTime.ofInstant(tsid.getInstant(), ZoneId.systemDefault())));
    }

    // Show TSID lexical sort order (using toString - should match creation order)
    System.out.println("\nTSID string sort order (should match creation order):");
    tsids.stream()
            .map(TSID::toString)
            .sorted()
            .forEach(id -> System.out.println("- String: " + id));

    // Show TSID numeric sort order (using toLong - should also match creation order)
    System.out.println("\nTSID long value sort order (should match creation order):");
    tsidLongs.stream()
            .sorted()
            .forEach(id -> System.out.println("- Long: " + id));

    // Show UUID lexical sort order (random, not time-based)
    System.out.println("\nUUID string sort order (random, not time-based):");
    uuids.stream()
            .map(UUID::toString)
            .sorted()
            .forEach(id -> System.out.println("- " + id));
  }

  /**
   * Compares string representation efficiency of different ID types
   */
  private static void compareStringRepresentation() {
    System.out.println("\n=== ID Representation Comparison ===");

    TSID tsid = TSID.fast();
    UUID uuid = UUID.randomUUID();

    String tsidString = tsid.toString();
    String uuidString = uuid.toString();
    long tsidLong = tsid.toLong();

    System.out.println("TSID as long (for database storage): " + tsidLong);
    System.out.println("TSID as string: " + tsidString);
    System.out.println("UUID as string: " + uuidString);

    System.out.println("\nStorage Requirements:");
    System.out.println("TSID as long: 8 bytes");
    System.out.println("TSID string length: " + tsidString.length() + " characters");
    System.out.println("UUID string length: " + uuidString.length() + " characters");

    System.out.printf("TSID is %.0f%% smaller than UUID in binary form (8 vs 16 bytes)%n",
            (1 - 8.0 / 16.0) * 100);
    System.out.printf("TSID string is %.0f%% shorter than UUID string%n",
            (1 - (double) tsidString.length() / uuidString.length()) * 100);

    // URL safety examples
    System.out.println("\n=== URL Examples ===");

    // Create sample IDs
    UUID uuid1 = UUID.randomUUID();
    TSID tsid1 = TSID.fast();
    int sequentialId = 1234567;

    // Example API endpoints
    String baseUrl = "https://api.example.com";

    System.out.println("REST API URL Examples:");

    // Sequential ID example
    String sequentialUrl = baseUrl + "/users/" + sequentialId;
    System.out.println("Sequential ID: " + sequentialUrl);
    System.out.println("  - Length: " + sequentialUrl.length() + " characters");
    System.out.println("  - Problem: Reveals information about total users, enumeration possible");

    // UUID example - standard format with hyphens
    String uuidUrl = baseUrl + "/users/" + uuid1;
    System.out.println("UUID (standard): " + uuidUrl);
    System.out.println("  - Length: " + uuidUrl.length() + " characters");
    System.out.println("  - No information leakage, but very long");

    // UUID example - without hyphens for URL safety
    String uuidCompactUrl = baseUrl + "/users/" + uuid1.toString().replace("-", "");
    System.out.println("UUID (compact): " + uuidCompactUrl);
    System.out.println("  - Length: " + uuidCompactUrl.length() + " characters");
    System.out.println("  - Still 32 characters long");

    // TSID example - string representation
    String tsidUrl = baseUrl + "/users/" + tsid1;
    System.out.println("TSID (string): " + tsidUrl);
    System.out.println("  - Length: " + tsidUrl.length() + " characters");
    System.out.println("  - URL-safe by default, naturally time-ordered");
    System.out.println("  - 13 characters vs 32/36 for UUID");

    // TSID example - long value as string
    String tsidLongUrl = baseUrl + "/users/" + tsid1.toLong();
    System.out.println("TSID (long value): " + tsidLongUrl);
    System.out.println("  - Length: " + tsidLongUrl.length() + " characters");
    System.out.println("  - Numeric representation, also time-ordered");
    System.out.println("  - Potentially even shorter in some cases");

    // Query parameter examples
    System.out.println("\nQuery Parameter Examples:");

    String uuidQueryUrl = baseUrl + "/search?user_id=" + uuid1;
    System.out.println("UUID in query: " + uuidQueryUrl);
    System.out.println("  - Length: " + uuidQueryUrl.length() + " characters");
    System.out.println("  - Hyphens might need URL encoding in some contexts");

    String tsidQueryUrl = baseUrl + "/search?user_id=" + tsid1;
    System.out.println("TSID in query: " + tsidQueryUrl);
    System.out.println("  - Length: " + tsidQueryUrl.length() + " characters");
    System.out.println("  - No encoding needed, Base32 is URL-safe");

    System.out.println("\nURL Path Component Length Comparison:");
    System.out.printf("TSID string is %.0f%% shorter than standard UUID format in URLs%n",
            (1 - (double) tsid1.toString().length() / uuid1.toString().length()) * 100);
  }
}
