package org.observability.otel.unit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.observability.otel.rest.CustomerPageResponse;
import org.springframework.data.domain.Limit;

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
    assertThat(capturedEntity.getId()).isEqualTo(result.id());
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
    entity.setId(basicCustomer.id());
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
    when(customerRepository.findById(updatedCustomer.id())).thenReturn(Optional.of(basicEntity));
    when(customerRepository.saveAndFlush(any())).thenThrow(new DataAccessException("DB Error") {});

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
    // Given — production code calls saveAndFlush, not save
    when(customerRepository.existsById(any(Long.class))).thenReturn(true);
    when(customerRepository.findById(any(Long.class))).thenReturn(Optional.of(basicEntity));
    when(customerRepository.saveAndFlush(any(CustomerEntity.class)))
        .thenThrow(new DataAccessException("Database error") {});

    // When / Then
    assertThatThrownBy(() -> customerService.update(basicCustomer.id(), basicCustomer))
        .isInstanceOf(CustomerServiceException.class);

    // saveAndFlush was called, but event publisher must not be reached
    verify(customerRepository).saveAndFlush(any(CustomerEntity.class));
    verifyNoInteractions(eventPublisher);
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

  // getCustomers (keyset pagination) tests
  @Test
  @DisplayName("Should return first page with hasMore=true when more results exist")
  void shouldReturnFirstPageWithHasMore() throws Exception {
    // 3 entities exist, limit=2 → fetch 3, hasMore=true, return first 2
    List<CustomerEntity> threeEntities = List.of(basicEntity, fullEntity,
        CustomerTestDataProvider.createBasicCustomerEntity());
    when(customerRepository.findNextPage(null, Limit.of(3))).thenReturn(threeEntities);

    CustomerPageResponse result = customerService.getCustomers(null, 2);

    assertThat(result.hasMore()).isTrue();
    assertThat(result.data()).hasSize(2);
    assertThat(result.nextCursor()).isNotNull();
    assertThat(result.limit()).isEqualTo(2);
    verify(customerRepository).findNextPage(null, Limit.of(3));
  }

  @Test
  @DisplayName("Should return last page with hasMore=false when fewer results than limit exist")
  void shouldReturnLastPageWithNoMore() throws Exception {
    // Only 1 entity after cursor, limit=2 → fetch 3, hasMore=false
    List<CustomerEntity> oneEntity = List.of(fullEntity);
    when(customerRepository.findNextPage(basicEntity.getId(), Limit.of(3))).thenReturn(oneEntity);

    CustomerPageResponse result = customerService.getCustomers(basicEntity.getId(), 2);

    assertThat(result.hasMore()).isFalse();
    assertThat(result.data()).hasSize(1);
    assertThat(result.nextCursor()).isNull();
    assertThat(result.limit()).isEqualTo(2);
    verify(customerRepository).findNextPage(basicEntity.getId(), Limit.of(3));
  }

  @Test
  @DisplayName("Should return empty page when no customers exist")
  void shouldReturnEmptyPageWhenNoCustomersExist() {
    when(customerRepository.findNextPage(null, Limit.of(3))).thenReturn(List.of());

    CustomerPageResponse result = customerService.getCustomers(null, 2);

    assertThat(result.hasMore()).isFalse();
    assertThat(result.data()).isEmpty();
    assertThat(result.nextCursor()).isNull();
    verify(customerRepository).findNextPage(null, Limit.of(3));
  }

  @Test
  @DisplayName("Should handle unexpected exceptions gracefully in translateAndThrow")
  void shouldHandleUnexpectedExceptionsGracefully() {
    when(customerRepository.findById(basicCustomer.id())).thenReturn(Optional.of(basicEntity));
    doThrow(new RuntimeException("Unexpected error")).when(customerRepository).deleteById(basicCustomer.id());

    assertThatThrownBy(() -> customerService.delete(basicCustomer.id()))
        .isInstanceOf(CustomerServiceException.class)
        .hasMessageContaining("unexpected error");
  }

  // -----------------------------------------------------------------------
  // findByEmail tests
  // -----------------------------------------------------------------------

  @Test
  @DisplayName("findByEmail - repository returns value - returns mapped Customer")
  void findByEmail_repositoryReturnsValue_returnsMappedCustomer() throws Exception {
    CustomerEntity entity = new CustomerEntity();
    entity.setId(basicCustomer.id());
    entity.setCustomerJson(objectMapper.writeValueAsString(basicCustomer));

    when(customerRepository.findByEmail("user@example.com")).thenReturn(Optional.of(entity));

    Customer result = customerService.findByEmail("user@example.com");

    assertThat(result).usingRecursiveComparison().isEqualTo(basicCustomer);
    verify(customerRepository).findByEmail("user@example.com");
  }

  @Test
  @DisplayName("findByEmail - repository returns empty - throws CustomerNotFoundException")
  void findByEmail_repositoryReturnsEmpty_throwsCustomerNotFoundException() {
    when(customerRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> customerService.findByEmail("missing@example.com"))
        .isInstanceOf(CustomerNotFoundException.class)
        .hasMessageContaining("No customer found with email: missing@example.com");

    verify(customerRepository).findByEmail("missing@example.com");
  }

  // -----------------------------------------------------------------------
  // findBySSN tests
  // -----------------------------------------------------------------------

  @Test
  @DisplayName("findBySSN - repository returns value - returns mapped Customer")
  void findBySSN_repositoryReturnsValue_returnsMappedCustomer() throws Exception {
    CustomerEntity entity = new CustomerEntity();
    entity.setId(basicCustomer.id());
    entity.setCustomerJson(objectMapper.writeValueAsString(basicCustomer));

    when(customerRepository.findBySSN("123-45-6789")).thenReturn(Optional.of(entity));

    Customer result = customerService.findBySSN("123-45-6789");

    assertThat(result).usingRecursiveComparison().isEqualTo(basicCustomer);
    verify(customerRepository).findBySSN("123-45-6789");
  }

  @Test
  @DisplayName("findBySSN - repository returns empty - throws CustomerNotFoundException")
  void findBySSN_repositoryReturnsEmpty_throwsCustomerNotFoundException() {
    when(customerRepository.findBySSN("000-00-0000")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> customerService.findBySSN("000-00-0000"))
        .isInstanceOf(CustomerNotFoundException.class)
        .hasMessageContaining("No customer found with SSN provided");

    verify(customerRepository).findBySSN("000-00-0000");
  }

  // Patch Tests
  @Test
  @DisplayName("patch() - applies firstName change only, other fields preserved")
  void shouldPatchFirstNameOnly() throws Exception {
    CustomerEntity entity = new CustomerEntity();
    entity.setId(basicCustomer.id());
    entity.setCustomerJson(objectMapper.writeValueAsString(basicCustomer));

    when(customerRepository.findById(basicCustomer.id())).thenReturn(Optional.of(entity));
    when(customerRepository.existsById(basicCustomer.id())).thenReturn(true);
    when(customerRepository.saveAndFlush(any(CustomerEntity.class))).thenAnswer(i -> i.getArgument(0));

    String patchJson = "{\"firstName\":\"PatchedName\"}";
    Customer result = customerService.patch(basicCustomer.id(), patchJson);

    assertThat(result.firstName()).isEqualTo("PatchedName");
    assertThat(result.lastName()).isEqualTo(basicCustomer.lastName());
    assertThat(result.type()).isEqualTo(basicCustomer.type());
    assertThat(result.emails()).isEqualTo(basicCustomer.emails());
    verify(eventPublisher).publishCustomerUpdated(any());
  }

  @Test
  @DisplayName("patch() - applies nested emails change")
  void shouldPatchEmails() throws Exception {
    CustomerEntity entity = new CustomerEntity();
    entity.setId(basicCustomer.id());
    entity.setCustomerJson(objectMapper.writeValueAsString(basicCustomer));

    when(customerRepository.findById(basicCustomer.id())).thenReturn(Optional.of(entity));
    when(customerRepository.existsById(basicCustomer.id())).thenReturn(true);
    when(customerRepository.saveAndFlush(any(CustomerEntity.class))).thenAnswer(i -> i.getArgument(0));

    String patchJson = "{\"emails\":[{\"primary\":true,\"email\":\"newemail@example.com\",\"type\":\"PERSONAL\"}]}";
    Customer result = customerService.patch(basicCustomer.id(), patchJson);

    assertThat(result.emails()).hasSize(1);
    // Email is package-private in org.observability.otel.domain; access fields via JsonNode
    com.fasterxml.jackson.databind.JsonNode emailNode = objectMapper.valueToTree(result.emails().get(0));
    assertThat(emailNode.get("email").asText()).isEqualTo("newemail@example.com");
    assertThat(result.firstName()).isEqualTo(basicCustomer.firstName());
    assertThat(result.lastName()).isEqualTo(basicCustomer.lastName());
  }

  @Test
  @DisplayName("patch() - id not found throws CustomerNotFoundException")
  void shouldThrowNotFoundWhenPatchingNonExistentCustomer() {
    when(customerRepository.findById(999L))
        .thenThrow(new org.springframework.dao.EmptyResultDataAccessException("Customer not found", 1));

    assertThatThrownBy(() -> customerService.patch(999L, "{\"firstName\":\"X\"}"))
        .isInstanceOf(CustomerNotFoundException.class)
        .hasMessageContaining("Error retrieving customer");
  }

  @Test
  @DisplayName("patch() - malformed patch JSON throws IllegalArgumentException")
  void shouldThrowIllegalArgumentExceptionForMalformedPatchJson() throws Exception {
    CustomerEntity entity = new CustomerEntity();
    entity.setId(basicCustomer.id());
    entity.setCustomerJson(objectMapper.writeValueAsString(basicCustomer));

    when(customerRepository.findById(basicCustomer.id())).thenReturn(Optional.of(entity));

    assertThatThrownBy(() -> customerService.patch(basicCustomer.id(), "{invalid json}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Failed to apply patch");
  }

}
