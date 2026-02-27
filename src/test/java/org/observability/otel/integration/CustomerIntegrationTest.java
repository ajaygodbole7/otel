package org.observability.otel.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.observability.otel.rest.ApiConstants.ApiPath.BASE_V1_API_PATH;
import static org.observability.otel.rest.ApiConstants.ApiPath.CUSTOMERS;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.cloudevents.CloudEvent;
import io.cloudevents.kafka.CloudEventDeserializer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.stream.StreamSupport;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.observability.otel.domain.Customer;
import org.observability.otel.domain.CustomerEntity;
import org.observability.otel.domain.CustomerRepository;
import org.observability.otel.rest.CustomerPageResponse;
import org.observability.otel.domain.CustomerTestDataProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/*
* Comprehensive End to End Integration Test
* Tests REST endpoints by making API requests
*  and verifies:
*   1.Database state is consistent
*   2. Correct Kafka Event is Published by subscribing to the Kafka event
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class CustomerIntegrationTest {
  private static final Logger log = LoggerFactory.getLogger(CustomerIntegrationTest.class);
  private static final String TOPIC = "customer-events";

  @Container
  @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16.2"));

  @Container
  static KafkaContainer kafka = new KafkaContainer("apache/kafka-native:3.8.0");

  @DynamicPropertySource
  static void kafkaProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
  }

  @LocalServerPort
  private int port;

  @Autowired
  private TestRestTemplate restTemplate;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private CustomerRepository customerRepository;

  private KafkaConsumer<String, CloudEvent> kafkaConsumer;
  private String baseUrl;

  @BeforeEach
  void setUp() {
    customerRepository.deleteAll();
    setupKafkaConsumer();
    baseUrl = String.format("http://localhost:%d%s", port, BASE_V1_API_PATH + CUSTOMERS);
  }

  @AfterEach
  void tearDown() {
    if (kafkaConsumer != null) {
      kafkaConsumer.close();
    }
  }

  @Test
  @DisplayName("Should create customer and verify Kafka event")
  void shouldCreateCustomer() throws Exception {
    // Create a customer
    Customer basicCustomer = CustomerTestDataProvider.createBasicCustomer();
    log.info("Creating customer: {}", basicCustomer);

    ResponseEntity<Customer> createResponse = restTemplate.postForEntity(baseUrl, basicCustomer, Customer.class);

    assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    Customer createdCustomer = createResponse.getBody();
    assertThat(createdCustomer).isNotNull();
    assertThat(createdCustomer.id()).isNotNull();
    assertThat(createdCustomer.createdAt()).isNotNull();
    assertThat(createdCustomer.updatedAt()).isNotNull();

    // Verify database state
    await()
        .atMost(5, TimeUnit.SECONDS)
        .untilAsserted(() -> {
          Optional<CustomerEntity> savedEntity = customerRepository.findById(createdCustomer.id());
          assertThat(savedEntity).isPresent();

          Customer storedCustomer = objectMapper.readValue(
              savedEntity.get().getCustomerJson(), Customer.class);
          assertThat(storedCustomer)
              .usingRecursiveComparison()
              .ignoringFields("createdAt", "updatedAt")
              .isEqualTo(createdCustomer);
        });

    // Verify Kafka event
    ConsumerRecords<String, CloudEvent> records = kafkaConsumer.poll(Duration.ofSeconds(10));
    List<ConsumerRecord<String, CloudEvent>> createEvents = filterEventsByType(records, "Customer::created");

    assertThat(createEvents).hasSize(1);

    CloudEvent event = createEvents.get(0).value();
    log.info("Create event received: {}", event);

    assertThat(event.getType()).isEqualTo("Customer::created");
    assertThat(event.getSource().toString()).isEqualTo("/customer/events");
    assertThat(event.getSubject()).isEqualTo(createdCustomer.id().toString());

    Customer eventCustomer = objectMapper.readValue(
        new String(event.getData().toBytes()), Customer.class);
    log.info("Deserialized Cloud event: {}", eventCustomer);
    assertThat(eventCustomer)
        .usingRecursiveComparison()
        .ignoringFields("updatedAt")
        .isEqualTo(createdCustomer);
  }

  @Test
  @DisplayName("Should create and update customer with Kafka events")
  void shouldCreateAndUpdateCustomer() throws Exception {
    // Create a customer
    Customer basicCustomer = CustomerTestDataProvider.createBasicCustomer();
    ResponseEntity<Customer> createResponse = restTemplate.postForEntity(
        baseUrl, basicCustomer, Customer.class);

    assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    Customer createdCustomer = createResponse.getBody();
    assertThat(createdCustomer).isNotNull();

    // Clear initial create event
    kafkaConsumer.poll(Duration.ofSeconds(10));

    // Update the customer
    Customer customerToUpdate = Customer.builder()
        .id(createdCustomer.id())
        .type(createdCustomer.type())
        .firstName("Updated First Name")
        .lastName("Updated Last Name")
        .emails(createdCustomer.emails())
        .phones(createdCustomer.phones())
        .addresses(createdCustomer.addresses())
        .createdAt(createdCustomer.createdAt())
        .build();

    log.info("Updating customer: {}", customerToUpdate);

    ResponseEntity<Customer> updateResponse = restTemplate.exchange(
        baseUrl + "/" + createdCustomer.id(),
        HttpMethod.PUT,
        new HttpEntity<>(customerToUpdate),
        Customer.class);

    log.info("Update invocation status: {} and body: {}",
        updateResponse.getStatusCode(), updateResponse.getBody());
    assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    Customer updatedCustomer = updateResponse.getBody();
    assertThat(updatedCustomer).isNotNull();

    // Verify database state
    await()
        .atMost(5, TimeUnit.SECONDS)
        .untilAsserted(() -> {
          Optional<CustomerEntity> entity = customerRepository.findById(updatedCustomer.id());
          assertThat(entity).isPresent();
          Customer savedCustomer = objectMapper.readValue(
              entity.get().getCustomerJson(), Customer.class);
          log.info("Saved customer: {}", savedCustomer);
          assertThat(savedCustomer.firstName()).isEqualTo("Updated First Name");
          assertThat(savedCustomer.lastName()).isEqualTo("Updated Last Name");
          assertThat(savedCustomer)
              .usingRecursiveComparison()
              .ignoringFields("firstName", "lastName", "createdAt", "updatedAt")
              .isEqualTo(createdCustomer);
        });

    // Verify Kafka event
    ConsumerRecords<String, CloudEvent> records = kafkaConsumer.poll(Duration.ofSeconds(10));
    List<ConsumerRecord<String, CloudEvent>> updateEvents = filterEventsByType(records, "Customer::updated");

    assertThat(updateEvents).hasSize(1);

    CloudEvent event = updateEvents.get(0).value();
    log.info("Update event received: {}", event);

    assertThat(event.getType()).isEqualTo("Customer::updated");
    assertThat(event.getSource().toString()).isEqualTo("/customer/events");
    assertThat(event.getSubject()).isEqualTo(updatedCustomer.id().toString());

    Customer eventCustomer = objectMapper.readValue(
        new String(event.getData().toBytes()), Customer.class);
    assertThat(eventCustomer)
        .usingRecursiveComparison()
        .ignoringFields("updatedAt")
        .isEqualTo(updatedCustomer);
  }

  @Test
  @DisplayName("Should delete customer and verify Kafka event")
  void shouldDeleteCustomer() throws Exception {
    // First create a customer
    Customer basicCustomer = CustomerTestDataProvider.createBasicCustomer();
    ResponseEntity<Customer> createResponse = restTemplate.postForEntity(
        baseUrl, basicCustomer, Customer.class);
    Customer createdCustomer = createResponse.getBody();
    assertThat(createdCustomer).isNotNull();

    // Clear initial create event
    kafkaConsumer.poll(Duration.ofSeconds(10));

    // Delete the customer and verify response
    log.info("Deleting customer: {}", createdCustomer);
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setAccept(List.of(MediaType.APPLICATION_JSON));
    ResponseEntity<Void> deleteResponse = restTemplate.exchange(
        baseUrl + "/" + createdCustomer.id(),
        HttpMethod.DELETE,
        new HttpEntity<>(headers),
        Void.class);
    assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

    // Verify database state - customer should be deleted
    await()
        .atMost(5, TimeUnit.SECONDS)
        .untilAsserted(() ->
            assertThat(customerRepository.findById(createdCustomer.id())).isEmpty());

    // Verify Kafka event
    ConsumerRecords<String, CloudEvent> records = kafkaConsumer.poll(Duration.ofSeconds(10));
    List<ConsumerRecord<String, CloudEvent>> deleteEvents = filterEventsByType(records, "Customer::deleted");

    assertThat(deleteEvents).hasSize(1);

    CloudEvent event = deleteEvents.get(0).value();
    log.info("Delete event received: {}", event);

    assertThat(event.getType()).isEqualTo("Customer::deleted");
    assertThat(event.getSource().toString()).isEqualTo("/customer/events");
    assertThat(event.getSubject()).isEqualTo(createdCustomer.id().toString());

    Customer eventCustomer = objectMapper.readValue(
        new String(event.getData().toBytes()), Customer.class);
    assertThat(eventCustomer)
        .usingRecursiveComparison()
        .ignoringFields("updatedAt")
        .isEqualTo(createdCustomer);
  }

  @Test
  @DisplayName("Should PATCH customer firstName and verify via GET")
  void shouldPatchCustomerFirstName() throws Exception {
    // Create a customer first
    Customer basicCustomer = CustomerTestDataProvider.createBasicCustomer();
    ResponseEntity<Customer> createResponse = restTemplate.postForEntity(baseUrl, basicCustomer, Customer.class);
    assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    Customer createdCustomer = createResponse.getBody();
    assertThat(createdCustomer).isNotNull();

    String originalLastName = createdCustomer.lastName();

    // PATCH only firstName
    HttpHeaders patchHeaders = new HttpHeaders();
    patchHeaders.setContentType(MediaType.parseMediaType("application/merge-patch+json"));
    patchHeaders.setAccept(List.of(MediaType.APPLICATION_JSON));

    String patchBody = "{\"firstName\":\"PatchedFirst\"}";
    ResponseEntity<Customer> patchResponse = restTemplate.exchange(
        baseUrl + "/" + createdCustomer.id(),
        HttpMethod.PATCH,
        new HttpEntity<>(patchBody, patchHeaders),
        Customer.class);

    assertThat(patchResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    Customer patchedCustomer = patchResponse.getBody();
    assertThat(patchedCustomer).isNotNull();
    assertThat(patchedCustomer.firstName()).isEqualTo("PatchedFirst");
    assertThat(patchedCustomer.lastName()).isEqualTo(originalLastName);

    // GET to confirm via independent fetch
    ResponseEntity<Customer> getResponse = restTemplate.getForEntity(
        baseUrl + "/" + createdCustomer.id(), Customer.class);
    assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    Customer fetchedCustomer = getResponse.getBody();
    assertThat(fetchedCustomer).isNotNull();
    assertThat(fetchedCustomer.firstName()).isEqualTo("PatchedFirst");
    assertThat(fetchedCustomer.lastName()).isEqualTo(originalLastName);
  }

  @Test
  @DisplayName("Should return 404 when PATCHing non-existent customer")
  void shouldReturn404WhenPatchingNonExistentCustomer() {
    HttpHeaders patchHeaders = new HttpHeaders();
    patchHeaders.setContentType(MediaType.parseMediaType("application/merge-patch+json"));
    patchHeaders.setAccept(List.of(MediaType.APPLICATION_JSON, MediaType.APPLICATION_PROBLEM_JSON));

    ResponseEntity<String> response = restTemplate.exchange(
        baseUrl + "/999999999",
        HttpMethod.PATCH,
        new HttpEntity<>("{\"firstName\":\"X\"}", patchHeaders),
        String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  // -----------------------------------------------------------------------
  // Search endpoint integration tests
  // -----------------------------------------------------------------------

  @Test
  @DisplayName("Should find customer by email after creation")
  void shouldFindCustomerByEmail() throws Exception {
    // Create a full customer that has an email
    Customer fullCustomer = CustomerTestDataProvider.createFullCustomer();
    ResponseEntity<Customer> createResponse = restTemplate.postForEntity(baseUrl, fullCustomer, Customer.class);
    assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    Customer createdCustomer = createResponse.getBody();
    assertThat(createdCustomer).isNotNull();

    // Extract the primary email via JsonNode (Email record is package-private)
    com.fasterxml.jackson.databind.JsonNode customerNode = objectMapper.valueToTree(createdCustomer);
    String email = StreamSupport.stream(customerNode.get("emails").spliterator(), false)
        .filter(e -> e.get("primary").asBoolean())
        .map(e -> e.get("email").asText())
        .findFirst()
        .orElseThrow(() -> new AssertionError("No primary email found on created customer"));

    // Search by email — use URI template to avoid double-encoding by RestTemplate
    ResponseEntity<Customer> searchResponse = restTemplate.getForEntity(
        baseUrl + "/search?email={email}", Customer.class, email);

    assertThat(searchResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    Customer foundCustomer = searchResponse.getBody();
    assertThat(foundCustomer).isNotNull();
    assertThat(foundCustomer.id()).isEqualTo(createdCustomer.id());
  }

  @Test
  @DisplayName("Should return 404 when searching by non-existent email")
  void shouldReturn404WhenSearchingByNonExistentEmail() {
    String searchUrl = baseUrl + "/search?email=nonexistent_nobody@example.com";

    ResponseEntity<String> searchResponse = restTemplate.getForEntity(searchUrl, String.class);

    assertThat(searchResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  @DisplayName("Should return 409 Conflict when creating a customer with an existing ID")
  void shouldReturn409WhenCreatingDuplicateCustomer() {
    // First creation
    Customer basicCustomer = CustomerTestDataProvider.createBasicCustomer();
    ResponseEntity<Customer> firstCreate = restTemplate.postForEntity(baseUrl, basicCustomer, Customer.class);
    assertThat(firstCreate.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    Customer created = firstCreate.getBody();
    assertThat(created).isNotNull();

    // Second creation with the same ID — must be 409
    ResponseEntity<String> secondCreate = restTemplate.postForEntity(baseUrl, created, String.class);

    assertThat(secondCreate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(secondCreate.getBody()).contains("Customer Conflict");
  }

  @Test
  @DisplayName("Should return paginated results and honour cursor across pages")
  void shouldReturnPaginatedCustomers() {
    // Create 3 customers to ensure we have enough data for two pages at limit=2
    for (int i = 0; i < 3; i++) {
      ResponseEntity<Customer> r = restTemplate.postForEntity(
          baseUrl, CustomerTestDataProvider.createBasicCustomer(), Customer.class);
      assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    // First page: limit=2
    ResponseEntity<CustomerPageResponse> firstPageResp = restTemplate.getForEntity(
        baseUrl + "?limit=2", CustomerPageResponse.class);
    assertThat(firstPageResp.getStatusCode()).isEqualTo(HttpStatus.OK);
    CustomerPageResponse page1 = firstPageResp.getBody();
    assertThat(page1).isNotNull();
    assertThat(page1.data()).hasSize(2);
    assertThat(page1.hasMore()).isTrue();
    assertThat(page1.nextCursor()).isNotNull();
    assertThat(page1.limit()).isEqualTo(2);

    // Second page: use cursor from first page
    ResponseEntity<CustomerPageResponse> secondPageResp = restTemplate.getForEntity(
        baseUrl + "?limit=2&after=" + page1.nextCursor(), CustomerPageResponse.class);
    assertThat(secondPageResp.getStatusCode()).isEqualTo(HttpStatus.OK);
    CustomerPageResponse page2 = secondPageResp.getBody();
    assertThat(page2).isNotNull();
    assertThat(page2.data()).isNotEmpty();

    // No overlapping IDs between pages
    List<Long> page1Ids = page1.data().stream().map(Customer::id).toList();
    List<Long> page2Ids = page2.data().stream().map(Customer::id).toList();
    assertThat(page2Ids).doesNotContainAnyElementsOf(page1Ids);

    // All IDs on page 2 are greater than the cursor (keyset ordering)
    page2Ids.forEach(id -> assertThat(id).isGreaterThan(page1.nextCursor()));
  }

  @Test
  @DisplayName("Should PATCH customer firstName and verify Customer::updated Kafka event")
  void shouldPatchCustomerAndVerifyKafkaEvent() throws Exception {
    // Create a customer
    Customer basicCustomer = CustomerTestDataProvider.createBasicCustomer();
    ResponseEntity<Customer> createResponse = restTemplate.postForEntity(baseUrl, basicCustomer, Customer.class);
    assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    Customer createdCustomer = createResponse.getBody();
    assertThat(createdCustomer).isNotNull();
    String originalLastName = createdCustomer.lastName();

    // Drain the create event so it doesn't bleed into the update assertion
    kafkaConsumer.poll(Duration.ofSeconds(10));

    // PATCH only firstName
    HttpHeaders patchHeaders = new HttpHeaders();
    patchHeaders.setContentType(MediaType.parseMediaType("application/merge-patch+json"));
    patchHeaders.setAccept(List.of(MediaType.APPLICATION_JSON));

    ResponseEntity<Customer> patchResponse = restTemplate.exchange(
        baseUrl + "/" + createdCustomer.id(),
        HttpMethod.PATCH,
        new HttpEntity<>("{\"firstName\":\"PatchedKafka\"}", patchHeaders),
        Customer.class);

    assertThat(patchResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    Customer patchedCustomer = patchResponse.getBody();
    assertThat(patchedCustomer).isNotNull();
    assertThat(patchedCustomer.firstName()).isEqualTo("PatchedKafka");
    assertThat(patchedCustomer.lastName()).isEqualTo(originalLastName);

    // Verify Customer::updated Kafka event
    ConsumerRecords<String, CloudEvent> records = kafkaConsumer.poll(Duration.ofSeconds(10));
    List<ConsumerRecord<String, CloudEvent>> updateEvents = filterEventsByType(records, "Customer::updated");

    assertThat(updateEvents).hasSize(1);
    CloudEvent event = updateEvents.get(0).value();
    assertThat(event.getType()).isEqualTo("Customer::updated");
    assertThat(event.getSource().toString()).isEqualTo("/customer/events");
    assertThat(event.getSubject()).isEqualTo(patchedCustomer.id().toString());

    Customer eventCustomer = objectMapper.readValue(new String(event.getData().toBytes()), Customer.class);
    assertThat(eventCustomer.firstName()).isEqualTo("PatchedKafka");
    assertThat(eventCustomer.lastName()).isEqualTo(originalLastName);
  }

  // -----------------------------------------------------------------------
  // Helpers
  // -----------------------------------------------------------------------

  private List<ConsumerRecord<String, CloudEvent>> filterEventsByType(
      ConsumerRecords<String, CloudEvent> records, String eventType) {
    return StreamSupport.stream(records.spliterator(), false)
        .filter(record -> record.value().getType().equals(eventType))
        .toList();
  }

  private void setupKafkaConsumer() {
    Properties props = new Properties();
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
    props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-group-" + System.currentTimeMillis());
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, CloudEventDeserializer.class);
    props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, true);

    kafkaConsumer = new KafkaConsumer<>(props);
    kafkaConsumer.subscribe(Collections.singletonList(TOPIC));

    // Initial poll to trigger partition assignment
    kafkaConsumer.poll(Duration.ofMillis(100));
  }
}
