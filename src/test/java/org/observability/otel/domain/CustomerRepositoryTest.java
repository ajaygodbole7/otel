package org.observability.otel.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.Limit;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
/*
*  Comprehensive end-end integration test of Customer Repository
*  using a Postgres Database using TestContainers
 */

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CustomerRepositoryTest {

  private static final Logger log = LoggerFactory.getLogger(CustomerRepositoryTest.class);

  @Container
  @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16.2")
      .withReuse(true);

  @Autowired
  private CustomerRepository customerRepository;

  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();
    objectMapper.registerModule(new JavaTimeModule());
  }

  @Test
  @DisplayName("Should save and retrieve basic customer via JSONB round-trip")
  void shouldSaveAndRetrieveBasicCustomer() throws JsonProcessingException {
    // Given
    var customer = CustomerTestDataProvider.createBasicCustomer();
    var entity = convertToEntity(customer);

    // When
    var saved = customerRepository.save(entity);
    customerRepository.flush();

    // Then
    customerRepository.findById(saved.getId())
        .map(this::convertToCustomer)
        .ifPresentOrElse(
            foundCustomer -> assertThat(foundCustomer)
                .usingRecursiveComparison()
                .isEqualTo(customer),
            () -> fail("Customer not found")
        );
  }

  @Test
  @DisplayName("Should save and retrieve full customer with all nested fields via JSONB round-trip")
  void shouldSaveAndRetrieveFullCustomer() throws JsonProcessingException {
    // Given
    var customer = CustomerTestDataProvider.createFullCustomer();
    var entity = convertToEntity(customer);

    // When
    var saved = customerRepository.save(entity);
    customerRepository.flush();

    // Then
    customerRepository.findById(saved.getId())
        .map(this::convertToCustomer)
        .ifPresentOrElse(
            foundCustomer -> assertThat(foundCustomer)
                .usingRecursiveComparison()
                .isEqualTo(customer),
            () -> fail("Customer not found")
        );
  }

  @Test
  @DisplayName("Should persist updated customer JSON and reflect new field values")
  void shouldUpdateExistingCustomer() throws JsonProcessingException {
    // Given
    var originalCustomer = CustomerTestDataProvider.createBasicCustomer();
    var entity = convertToEntity(originalCustomer);
    var saved = customerRepository.save(entity);
    customerRepository.flush();
    var initialUpdatedAt = saved.getUpdatedAt();


    // When
    var updatedCustomer = CustomerTestDataProvider.createUpdateCustomer(
        originalCustomer.id()
        ,originalCustomer.createdAt());

    saved.setCustomerJson(objectMapper.writeValueAsString(updatedCustomer));
    var updated = customerRepository.saveAndFlush(saved);

    // Then
    customerRepository.findById(saved.getId())
        .map(this::convertToCustomer)
        .ifPresentOrElse(
            foundUpdatedCustomer -> {
              assertThat(foundUpdatedCustomer.id()).isEqualTo(updatedCustomer.id());
              assertThat(foundUpdatedCustomer.createdAt()).isEqualTo(updatedCustomer.createdAt());
              assertThat(foundUpdatedCustomer.updatedAt()).isAfter(initialUpdatedAt);
              assertThat(foundUpdatedCustomer.firstName()).isEqualTo(updatedCustomer.firstName());
              assertThat(foundUpdatedCustomer.lastName()).isEqualTo(updatedCustomer.lastName());
              assertThat(foundUpdatedCustomer.emails().get(0).email())
                  .isEqualTo(updatedCustomer.emails().get(0).email());
            },
            () -> fail("Customer not found")
        );
  }

  @Test
  @DisplayName("Should return all saved customers")
  void shouldFindAllCustomers() throws JsonProcessingException {
    // Given: start from a known clean state so hasSize(2) is reliable
    customerRepository.deleteAll();
    var customer1 = convertToEntity(CustomerTestDataProvider.createBasicCustomer());
    var customer2 = convertToEntity(CustomerTestDataProvider.createFullCustomer());
    customerRepository.saveAll(List.of(customer1, customer2));
    customerRepository.flush();

    // When
    var customers = customerRepository.findAll();

    // Then
    assertThat(customers).hasSize(2);
  }

  @Test
  @DisplayName("Should find customer by email using JSONB path query")
  void shouldFindCustomerByEmail() throws JsonProcessingException {
    // Given
    var customer = CustomerTestDataProvider.createBasicCustomer();
    var entity = convertToEntity(customer);
    customerRepository.saveAndFlush(entity);

    log.debug("Saved customer JSON: {}", entity.getCustomerJson());
    log.debug("Querying for email: {}", customer.emails().get(0).email());


    // When
    var retrieved = customerRepository.findByEmail(customer.emails().get(0).email());

    // Then
    assertThat(retrieved).isPresent();
    retrieved.ifPresent(found -> assertThat(found.getId()).isEqualTo(customer.id()));
  }

  @Test
  @DisplayName("Should delete customer and confirm absence")
  void shouldDeleteCustomer() throws JsonProcessingException {
    // Given
    var customer = CustomerTestDataProvider.createBasicCustomer();
    var entity = convertToEntity(customer);
    var savedEntity = customerRepository.saveAndFlush(entity);

    // Ensure customer is saved
    assertThat(customerRepository.findById(savedEntity.getId())).isPresent();

    // When
    customerRepository.deleteById(savedEntity.getId());
    customerRepository.flush();

    // Then
    assertThat(customerRepository.findById(savedEntity.getId())).isNotPresent();
  }

  @Test
  @DisplayName("Should delete all customers and return empty result set")
  void shouldDeleteAllCustomers() throws JsonProcessingException {
    // Given
    var customer1 = convertToEntity(CustomerTestDataProvider.createBasicCustomer());
    var customer2 = convertToEntity(CustomerTestDataProvider.createBasicCustomer());
    customerRepository.saveAll(List.of(customer1, customer2));
    customerRepository.flush();

    // When
    customerRepository.deleteAll();
    customerRepository.flush();

    // Then
    assertThat(customerRepository.findAll()).isEmpty();
  }

  @Test
  @DisplayName("Should not throw when deleting a non-existent ID")
  void shouldHandleDeleteByNonExistentId() {
    // When
    customerRepository.deleteById(99999L); // Non-existent ID
    customerRepository.flush();

    // Then
    assertThat(customerRepository.findById(99999L)).isNotPresent(); // Ensures no error occurs
  }

  @Test
  @DisplayName("Should delete customer without explicit flush and confirm absence")
  void shouldDeleteCustomerWithoutFlushing() throws JsonProcessingException {
    // Given
    var customer = convertToEntity(CustomerTestDataProvider.createBasicCustomer());
    customerRepository.save(customer);

    // When
    customerRepository.deleteById(customer.getId());

    // Then
    assertThat(customerRepository.findById(customer.getId())).isNotPresent();
  }

  // findNextPage (keyset pagination) tests
  @Test
  @DisplayName("findNextPage(null, 3) with 5 customers should return first 3")
  void shouldReturnFirstPageOfCustomers() throws JsonProcessingException {
    // Given: save 5 customers with monotonically increasing TSID ids
    customerRepository.deleteAll();
    List<CustomerEntity> saved = new java.util.ArrayList<>();
    for (int i = 0; i < 5; i++) {
      var entity = convertToEntity(CustomerTestDataProvider.createBasicCustomer());
      saved.add(customerRepository.saveAndFlush(entity));
    }
    // Sort by id so we know ordering
    saved.sort(java.util.Comparator.comparingLong(CustomerEntity::getId));

    // When
    List<CustomerEntity> page = customerRepository.findNextPage(null, Limit.of(3));

    // Then: first 3 in id order
    assertThat(page).hasSize(3);
    assertThat(page.get(0).getId()).isEqualTo(saved.get(0).getId());
    assertThat(page.get(1).getId()).isEqualTo(saved.get(1).getId());
    assertThat(page.get(2).getId()).isEqualTo(saved.get(2).getId());
  }

  @Test
  @DisplayName("findNextPage(id_of_2nd, 3) should return 3rd, 4th, 5th customers")
  void shouldReturnNextPageAfterCursor() throws JsonProcessingException {
    // Given: save 5 customers
    customerRepository.deleteAll();
    List<CustomerEntity> saved = new java.util.ArrayList<>();
    for (int i = 0; i < 5; i++) {
      var entity = convertToEntity(CustomerTestDataProvider.createBasicCustomer());
      saved.add(customerRepository.saveAndFlush(entity));
    }
    saved.sort(java.util.Comparator.comparingLong(CustomerEntity::getId));

    Long cursorId = saved.get(1).getId(); // id of 2nd customer

    // When
    List<CustomerEntity> page = customerRepository.findNextPage(cursorId, Limit.of(3));

    // Then: 3rd, 4th, 5th
    assertThat(page).hasSize(3);
    assertThat(page.get(0).getId()).isEqualTo(saved.get(2).getId());
    assertThat(page.get(1).getId()).isEqualTo(saved.get(3).getId());
    assertThat(page.get(2).getId()).isEqualTo(saved.get(4).getId());
  }

  @Test
  @DisplayName("findNextPage(id_of_last, 3) should return empty list")
  void shouldReturnEmptyPageWhenCursorIsLastId() throws JsonProcessingException {
    // Given: save 5 customers
    customerRepository.deleteAll();
    List<CustomerEntity> saved = new java.util.ArrayList<>();
    for (int i = 0; i < 5; i++) {
      var entity = convertToEntity(CustomerTestDataProvider.createBasicCustomer());
      saved.add(customerRepository.saveAndFlush(entity));
    }
    saved.sort(java.util.Comparator.comparingLong(CustomerEntity::getId));

    Long lastId = saved.get(4).getId(); // id of last customer

    // When
    List<CustomerEntity> page = customerRepository.findNextPage(lastId, Limit.of(3));

    // Then: empty
    assertThat(page).isEmpty();
  }

  //Utility methods
  private CustomerEntity convertToEntity(Customer customer) throws JsonProcessingException {
    return CustomerEntity.builder()
        .id(customer.id())
        .customerJson(objectMapper.writeValueAsString(customer))
        .build();
  }

  private Customer convertToCustomer(CustomerEntity entity) {
    try {
      return objectMapper.readValue(entity.getCustomerJson(), Customer.class);
    } catch (JsonProcessingException e) {
      throw new RuntimeException("Error converting customer JSON", e);
    }
  }



}

