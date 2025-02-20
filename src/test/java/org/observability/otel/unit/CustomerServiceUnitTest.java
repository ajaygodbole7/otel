package org.observability.otel.unit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.observability.otel.domain.Customer;
import org.observability.otel.domain.CustomerEntity;
import org.observability.otel.domain.CustomerRepository;
import org.observability.otel.domain.CustomerTestDataProvider;
import org.observability.otel.exception.CustomerConflictException;
import org.observability.otel.exception.CustomerNotFoundException;
import org.observability.otel.exception.CustomerServiceException;
import org.observability.otel.exception.ServiceUnavailableException;
import org.observability.otel.service.CustomerEventPublisher;
import org.observability.otel.service.CustomerService;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.dao.TransientDataAccessResourceException;

@ExtendWith(MockitoExtension.class)
class CustomerServiceUnitTest {
  @Mock
  private CustomerRepository customerRepository;

  @Mock
  private CustomerEventPublisher eventPublisher;

  private ObjectMapper objectMapper;

  private CustomerService customerService;

  // Test data
  private Customer basicCustomer;
  private Customer fullCustomer;
  private Customer updatedCustomer;
  private CustomerEntity basicEntity;
  private CustomerEntity fullEntity;


  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);
    basicCustomer = CustomerTestDataProvider.createBasicCustomer();
    fullCustomer = CustomerTestDataProvider.createFullCustomer();
    updatedCustomer =
        CustomerTestDataProvider.createUpdateCustomer(
            basicCustomer.id(), basicCustomer.createdAt());
    basicEntity = CustomerTestDataProvider.createBasicCustomerEntity();
    fullEntity = CustomerTestDataProvider.createFullCustomerEntity();
    // Manually create the service with all dependencies
    customerService = new CustomerService(
        customerRepository,
        objectMapper,
        eventPublisher
    );
  }

  // Create Customer Tests
  @Test
  @DisplayName("Should create a new customer successfully")
  void shouldCreateCustomerSuccessfully() throws Exception {
    // Given
    ArgumentCaptor<CustomerEntity> entityCaptor = ArgumentCaptor.forClass(CustomerEntity.class);
    when(customerRepository.saveAndFlush(any(CustomerEntity.class)))
        .thenAnswer(
            invocation -> {
              CustomerEntity savedEntity = invocation.getArgument(0);
              return savedEntity; // Return the same entity that was saved
            });

    // When
    Customer result = customerService.create(basicCustomer);

    // Then
    assertThat(result)
        .usingRecursiveComparison()
        .ignoringFields("id", "createdAt", "updatedAt")
        .isEqualTo(basicCustomer);

    // Verify ID is set
    assertThat(result.id()).isNotNull();

    // Verify timestamps are set
    assertThat(result.createdAt()).isNotNull();
    assertThat(result.updatedAt()).isNotNull();
    assertThat(result.createdAt()).isEqualTo(result.updatedAt());

    // Verify Repository interactions
    verify(customerRepository).existsById(basicCustomer.id());
    verify(customerRepository).saveAndFlush(entityCaptor.capture());

    // Verify saved entity has correct ID and JSON
    CustomerEntity capturedEntity = entityCaptor.getValue();
    assertThat(capturedEntity.getCustomerId()).isEqualTo(result.id());
    assertThat(capturedEntity.getCustomerJson()).contains(result.id().toString());

   // Verify saved entity has correct ID and JSON
    assertThat(capturedEntity.getCustomerId()).isEqualTo(result.id());
    assertThat(capturedEntity.getCustomerJson()).contains(result.id().toString());

    // Verify event publishing
    verify(eventPublisher).publishCustomerCreated(result);
}
  @Test
  @DisplayName("Should publish event after successful creation")
  void shouldPublishEventAfterCreation() throws JsonProcessingException {
    when(customerRepository.saveAndFlush(any(CustomerEntity.class))).thenReturn(basicEntity);

    customerService.create(basicCustomer);

    // Capture the published customer
    ArgumentCaptor<Customer> customerCaptor = ArgumentCaptor.forClass(Customer.class);
    verify(eventPublisher).publishCustomerCreated(customerCaptor.capture());

    Customer publishedCustomer = customerCaptor.getValue();

    // Deserialize the JSON from the saved entity for accurate comparison
    Customer expectedCustomer = objectMapper.readValue(basicEntity.getCustomerJson(), Customer.class);

    // Compare key fields while ignoring dynamic timestamps
    assertThat(publishedCustomer)
        .usingRecursiveComparison()
        .ignoringFields("createdAt", "updatedAt")
        .isEqualTo(expectedCustomer);
  }

  @Test
  @DisplayName("Should throw CustomerConflictException when customer already exists")
  void shouldThrowConflictExceptionWhenCustomerExists() {
    when(customerRepository.existsById(any())).thenReturn(true);

    assertThatThrownBy(() -> customerService.create(basicCustomer))
        .isInstanceOf(CustomerConflictException.class)
        .hasMessageContaining("already exists");

    verify(customerRepository).existsById(basicCustomer.id());
    verify(customerRepository, never()).saveAndFlush(any());
    verify(eventPublisher, never()).publishCustomerCreated(any());
  }

  @Test
  @DisplayName("Should throw CustomerServiceException when save fails")
  void shouldThrowServiceExceptionWhenSaveFails() {

    when(customerRepository.existsById(any())).thenReturn(false);
    when(customerRepository.saveAndFlush(any())).thenThrow(new DataAccessException("DB Error") {});

    assertThatThrownBy(() -> customerService.create(basicCustomer))
        .isInstanceOf(CustomerServiceException.class)
        .hasMessageContaining("Error creating customer");
        //.hasCauseInstanceOf(DataAccessException.class);

    verify(customerRepository).existsById(basicCustomer.id());
    verify(customerRepository).saveAndFlush(any());
    verify(eventPublisher, never()).publishCustomerCreated(any());
  }

  @Test
  @DisplayName("Should throw IllegalArgumentException when creating a null customer")
  void shouldThrowExceptionWhenCreatingNullCustomer() {
    assertThatThrownBy(() -> customerService.create(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Customer must not be null");

    verify(customerRepository, never()).save(any());
    verify(eventPublisher, never()).publishCustomerCreated(any());
  }

  @Test
  @DisplayName("Should not publish event when customer creation fails due to database error")
  void shouldNotPublishEventOnCreateFailure() {
    // Given
    when(customerRepository.saveAndFlush(any(CustomerEntity.class)))
        .thenThrow(new DataAccessException("Database error") {});

    // When / Then
    assertThatThrownBy(() -> customerService.create(basicCustomer))
        .isInstanceOf(CustomerServiceException.class);

    // Verifications
    verify(customerRepository).saveAndFlush(any(CustomerEntity.class));
    verifyNoInteractions(eventPublisher); // Ensure no event is published
  }

  // Find By Id Tests
  @Test
  @DisplayName("Should find customer by id successfully - Happy Path")
  void shouldFindCustomerById() throws Exception {
    CustomerEntity entity = new CustomerEntity();
    entity.setCustomerId(basicCustomer.id());
    entity.setCustomerJson(objectMapper.writeValueAsString(basicCustomer));

    when(customerRepository.findById(basicCustomer.id())).thenReturn(Optional.of(entity));

    Customer result = customerService.findById(basicCustomer.id());

    assertThat(result)
        .usingRecursiveComparison()
        .isEqualTo(basicCustomer);

    verify(customerRepository).findById(basicCustomer.id());
  }

  @Test
  @DisplayName("Should throw CustomerNotFoundException when customer not found")
  void shouldThrowNotFoundExceptionWhenCustomerNotFound() {
    when(customerRepository.findById(999L))
        .thenThrow(new EmptyResultDataAccessException("Customer not found", 1));

    assertThatThrownBy(() -> customerService.findById(999L))
        .isInstanceOf(CustomerNotFoundException.class)
        .hasMessageContaining("Error retrieving customer");

    verify(customerRepository).findById(999L);
  }

  // Update Tests
  @Test
  @DisplayName("Should update customer - Happy Path")
  void shouldUpdateCustomer() throws Exception {
    when(customerRepository.existsById(any())).thenReturn(true);
    when(customerRepository.findById(any(Long.class))).thenReturn(Optional.ofNullable(basicEntity));
    when(customerRepository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));

    Customer result = customerService.update(updatedCustomer.id(), updatedCustomer);

    assertThat(result)
        .usingRecursiveComparison()
        .ignoringFields("id","createdAt","updatedAt")
        .isEqualTo(updatedCustomer);

    // Verify that the ignored fields exist
    assertThat(result.id()).isNotNull();
    assertThat(result.updatedAt()).isNotNull();
    assertThat(result.createdAt()).isNotNull();

    // Verify repository and event publisher interactions
    verify(customerRepository).existsById(updatedCustomer.id());
    verify(customerRepository).saveAndFlush(any());
    verify(eventPublisher).publishCustomerUpdated(result);
  }

  @Test
  @DisplayName("Should throw exception when updating non-existent customer")
  void shouldThrowWhenUpdatingNonExistent() {
    lenient().when(customerRepository.existsById(any())).thenReturn(false);

    assertThatThrownBy(() -> customerService.update(999L, updatedCustomer))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("Should throw CustomerServiceException when DataAccessException occurs during update")
  void shouldThrowServiceExceptionWhenUpdateFails() {
    when(customerRepository.existsById(any())).thenReturn(true);
    lenient().when(customerRepository.saveAndFlush(any())).thenThrow(new DataAccessException("DB Error") {});

    assertThatThrownBy(() -> customerService.update(updatedCustomer.id(), updatedCustomer))
        .isInstanceOf(CustomerServiceException.class)
        .hasMessageContaining("Error updating customer");
  }

  @Test
  @DisplayName("Should throw IllegalArgumentException when updating with null customer")
  void shouldThrowExceptionWhenUpdatingWithNull() {
    assertThatThrownBy(() -> customerService.update(basicCustomer.id(), null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Invalid customer data or ID mismatch");
  }

  @Test
  @DisplayName("Should not publish event when customer update fails due to database error")
  void shouldNotPublishEventOnUpdateFailure() {
    // Given
    lenient().when(customerRepository.findById(any(Long.class))).thenReturn(Optional.of(basicEntity));
    lenient().when(customerRepository.existsById(any(Long.class))).thenReturn(true);
    lenient().when(customerRepository.save(any(CustomerEntity.class)))
        .thenThrow(new DataAccessException("Database error") {});

    // When / Then
    assertThatThrownBy(() -> customerService.update(basicCustomer.id(), basicCustomer))
        .isInstanceOf(CustomerServiceException.class);

    // Verifications
    verify(customerRepository).saveAndFlush(any(CustomerEntity.class));
    verifyNoInteractions(eventPublisher); // Ensure no event is published
  }


  // Delete Tests
  @Test
  @DisplayName("Should delete customer - Happy Path")
  void shouldDeleteCustomer() throws Exception {
    when(customerRepository.findById(basicCustomer.id())).thenReturn(Optional.of(basicEntity));

    customerService.delete(basicCustomer.id());

    verify(customerRepository).deleteById(basicCustomer.id());
    verify(eventPublisher).publishCustomerDeleted(any());
  }

  @Test
  @DisplayName("Should handle transient error during operation")
  void shouldHandleTransientError() {
    when(customerRepository.findById(any()))
        .thenThrow(new TransientDataAccessResourceException("DB unavailable"));

    assertThatThrownBy(() -> customerService.findById(999L))
        .isInstanceOf(ServiceUnavailableException.class);
  }

  @Test
  @DisplayName("Should throw CustomerServiceException when DataAccessException occurs during deletion")
  void shouldThrowCustomerServiceExceptionOnDataAccessException() {
    // Given
    Long customerId = basicCustomer.id();
    when(customerRepository.findById(customerId)).thenReturn(Optional.of(basicEntity)); // Mocking findById
    doThrow(new DataAccessException("Database error") {})
        .when(customerRepository).deleteById(customerId); // Mocking deletion failure

    // When / Then
    assertThatThrownBy(() -> customerService.delete(customerId))
        .isInstanceOf(CustomerServiceException.class)
        ;

    // Verifications
    verify(customerRepository).findById(customerId);
    verify(customerRepository).deleteById(customerId);
    verifyNoInteractions(eventPublisher); // Ensure no event is published on failure
  }

  @Test
  @DisplayName("Should throw ServiceUnavailableException on QueryTimeoutException during deletion")
  void shouldThrowServiceUnavailableExceptionOnQueryTimeout() {
    // Given
    Long customerId = basicCustomer.id();
    when(customerRepository.findById(customerId)).thenReturn(Optional.of(basicEntity)); // Mocking findById
    doThrow(new QueryTimeoutException("Database timeout"))
        .when(customerRepository).deleteById(customerId);

    // When / Then
    assertThatThrownBy(() -> customerService.delete(customerId))
        .isInstanceOf(ServiceUnavailableException.class);

    verify(customerRepository).deleteById(customerId);
    verifyNoInteractions(eventPublisher);
  }

  @Test
  @DisplayName("Should handle deletion of non-existent customer gracefully")
  void shouldHandleDeletionOfNonExistentCustomer() {
    // Given
    Long nonExistentId = 999L;
    when(customerRepository.findById(nonExistentId)).thenReturn(Optional.of(basicEntity)); // Mocking findById
    doThrow(new EmptyResultDataAccessException(1))
        .when(customerRepository).deleteById(nonExistentId);

    // When / Then
    assertThatThrownBy(() -> customerService.delete(nonExistentId))
        .isInstanceOf(CustomerNotFoundException.class);

    verify(customerRepository).deleteById(nonExistentId);
    verifyNoInteractions(eventPublisher);
  }

  // find All tests
  @Test
  @DisplayName("Should retrieve all customers successfully")
  void shouldRetrieveAllCustomersSuccessfully() {
    when(customerRepository.findAll()).thenReturn(List.of(basicEntity, fullEntity));

    List<Customer> customers = customerService.getAllCustomers();


    assertThat(customers.stream()
                   .map(Customer::id)
                   .collect(Collectors.toList()))
        .containsExactlyInAnyOrder(basicEntity.getCustomerId(), fullEntity.getCustomerId());

    verify(customerRepository, times(1)).findAll();
  }

  @Test
  @DisplayName("Should return empty list when no customers are available")
  void shouldReturnEmptyListWhenNoCustomersAvailable() {
    when(customerRepository.findAll()).thenReturn(List.of());

    List<Customer> customers = customerService.getAllCustomers();

    assertThat(customers).isEmpty();
    verify(customerRepository, times(1)).findAll();
  }

  @Test
  @DisplayName("Should handle unexpected exceptions gracefully in translateAndThrow")
  void shouldHandleUnexpectedExceptionsGracefully() {
    RuntimeException unexpectedException = new RuntimeException("Unexpected error");

    assertThatThrownBy(() -> customerService.delete(basicCustomer.id()))
        .isInstanceOf(CustomerServiceException.class)
        .hasMessageContaining("unexpected error");
  }

}
