package org.observability.otel.unit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
import org.observability.otel.rest.CustomerPageResponse;
import org.observability.otel.service.CustomerEventPublisher;
import org.observability.otel.service.CustomerService;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.data.domain.Limit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerServiceUnitTest {

  @Mock
  private CustomerRepository customerRepository;

  @Mock
  private CustomerEventPublisher eventPublisher;

  @org.mockito.Spy
  jakarta.validation.Validator validator =
      jakarta.validation.Validation.buildDefaultValidatorFactory().getValidator();

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
    customerService = new CustomerService(customerRepository, objectMapper, eventPublisher, validator);
  }

  // -----------------------------------------------------------------------
  // Create Customer Tests
  // -----------------------------------------------------------------------

  @Test
  @DisplayName("Should create a new customer successfully")
  void shouldCreateCustomerSuccessfully() throws Exception {
    ArgumentCaptor<CustomerEntity> entityCaptor = ArgumentCaptor.forClass(CustomerEntity.class);
    when(customerRepository.saveAndFlush(any(CustomerEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    Customer result = customerService.create(basicCustomer);

    assertThat(result)
        .usingRecursiveComparison()
        .ignoringFields("id", "createdAt", "updatedAt")
        .isEqualTo(basicCustomer);
    assertThat(result.id()).isNotNull();
    assertThat(result.createdAt()).isNotNull();
    assertThat(result.updatedAt()).isNotNull();
    assertThat(result.createdAt()).isEqualTo(result.updatedAt());

    verify(customerRepository).existsById(basicCustomer.id());
    verify(customerRepository).saveAndFlush(entityCaptor.capture());
    CustomerEntity capturedEntity = entityCaptor.getValue();
    assertThat(capturedEntity.getId()).isEqualTo(result.id());
    assertThat(capturedEntity.getCustomerJson()).contains(result.id().toString());

    verify(eventPublisher).publishCustomerCreated(result);
  }

  @Test
  @DisplayName("Should publish event after successful creation")
  void shouldPublishEventAfterCreation() throws JsonProcessingException {
    when(customerRepository.saveAndFlush(any(CustomerEntity.class))).thenReturn(basicEntity);

    customerService.create(basicCustomer);

    ArgumentCaptor<Customer> customerCaptor = ArgumentCaptor.forClass(Customer.class);
    verify(eventPublisher).publishCustomerCreated(customerCaptor.capture());

    Customer publishedCustomer = customerCaptor.getValue();
    Customer expectedCustomer = objectMapper.readValue(basicEntity.getCustomerJson(), Customer.class);

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
  @DisplayName("Should throw CustomerConflictException when creating customer with an already-used email")
  void shouldThrowConflictOnDuplicateEmailDuringCreate() throws Exception {
    Customer customerWithEmail = CustomerTestDataProvider.createFullCustomer();

    // Extract the primary email using JsonNode (Email is package-private, can't call .email() directly)
    com.fasterxml.jackson.databind.JsonNode emailsNode =
        objectMapper.valueToTree(customerWithEmail).get("emails");
    String primaryEmail = java.util.stream.StreamSupport
        .stream(emailsNode.spliterator(), false)
        .filter(e -> e.get("primary") != null && e.get("primary").asBoolean())
        .map(e -> e.get("email").asText())
        .findFirst()
        .orElseThrow();

    CustomerEntity conflicting = CustomerEntity.builder()
        .id(99999L)
        .customerJson("{}")
        .build();

    when(customerRepository.existsById(any())).thenReturn(false);
    when(customerRepository.findByEmail(primaryEmail)).thenReturn(Optional.of(conflicting));

    assertThatThrownBy(() -> customerService.create(customerWithEmail))
        .isInstanceOf(CustomerConflictException.class)
        .hasMessageContaining("already exists");
  }

  @Test
  @DisplayName("Should throw CustomerServiceException and not publish event when saveAndFlush fails on create")
  void shouldThrowServiceExceptionAndNotPublishEventWhenCreateSaveFails() {
    when(customerRepository.saveAndFlush(any(CustomerEntity.class)))
        .thenThrow(new DataAccessException("DB write failure") {});

    assertThatThrownBy(() -> customerService.create(basicCustomer))
        .isInstanceOf(CustomerServiceException.class)
        .hasMessageContaining("Error creating customer");

    verify(customerRepository).saveAndFlush(any(CustomerEntity.class));
    verifyNoInteractions(eventPublisher);
  }

  @Test
  @DisplayName("Should throw IllegalArgumentException when creating a null customer")
  void shouldThrowExceptionWhenCreatingNullCustomer() {
    assertThatThrownBy(() -> customerService.create(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Customer must not be null");

    verify(customerRepository, never()).saveAndFlush(any());
    verify(eventPublisher, never()).publishCustomerCreated(any());
  }

  // -----------------------------------------------------------------------
  // Find By Id Tests
  // -----------------------------------------------------------------------

  @Test
  @DisplayName("Should find customer by id successfully - Happy Path")
  void shouldFindCustomerById() throws Exception {
    CustomerEntity entity = CustomerEntity.builder()
        .id(basicCustomer.id())
        .customerJson(objectMapper.writeValueAsString(basicCustomer))
        .build();

    when(customerRepository.findById(basicCustomer.id())).thenReturn(Optional.of(entity));

    Customer result = customerService.findById(basicCustomer.id());

    assertThat(result).usingRecursiveComparison().isEqualTo(basicCustomer);
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

  // -----------------------------------------------------------------------
  // Update Tests
  // -----------------------------------------------------------------------

  @Test
  @DisplayName("Should update customer - Happy Path")
  void shouldUpdateCustomer() throws Exception {
    when(customerRepository.findById(any(Long.class))).thenReturn(Optional.ofNullable(basicEntity));
    when(customerRepository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));

    Customer result = customerService.update(updatedCustomer.id(), updatedCustomer);

    assertThat(result)
        .usingRecursiveComparison()
        .ignoringFields("id", "createdAt", "updatedAt")
        .isEqualTo(updatedCustomer);
    assertThat(result.id()).isNotNull();
    assertThat(result.updatedAt()).isNotNull();
    assertThat(result.createdAt()).isNotNull();

    verify(customerRepository).saveAndFlush(any());
    verify(eventPublisher).publishCustomerUpdated(result);
  }

  @Test
  @DisplayName("Should throw IllegalArgumentException when path ID does not match customer ID")
  void shouldThrowIllegalArgumentExceptionWhenIdMismatches() {
    // updatedCustomer.id() == basicCustomer.id(); calling update with a different id (999L)
    // fails the id-mismatch check inside validateExistingCustomer before existsById is called.
    assertThatThrownBy(() -> customerService.update(999L, updatedCustomer))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Invalid customer data or ID mismatch");
  }

  @Test
  @DisplayName("Should throw CustomerServiceException when DataAccessException occurs during update")
  void shouldThrowServiceExceptionWhenUpdateFails() {
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
    when(customerRepository.findById(any(Long.class))).thenReturn(Optional.of(basicEntity));
    when(customerRepository.saveAndFlush(any(CustomerEntity.class)))
        .thenThrow(new DataAccessException("Database error") {});

    assertThatThrownBy(() -> customerService.update(basicCustomer.id(), basicCustomer))
        .isInstanceOf(CustomerServiceException.class);

    verify(customerRepository).saveAndFlush(any(CustomerEntity.class));
    verifyNoInteractions(eventPublisher);
  }

  // -----------------------------------------------------------------------
  // Delete Tests
  // -----------------------------------------------------------------------

  @Test
  @DisplayName("Should delete customer - Happy Path")
  void shouldDeleteCustomer() {
    when(customerRepository.findById(basicCustomer.id())).thenReturn(Optional.of(basicEntity));

    customerService.delete(basicCustomer.id());

    verify(customerRepository).deleteById(basicCustomer.id());
    verify(eventPublisher).publishCustomerDeleted(any());
  }

  @Test
  @DisplayName("Should throw CustomerNotFoundException when deleting a non-existent customer")
  void shouldThrowNotFoundWhenDeletingNonExistentCustomer() {
    // findById returns empty → service's findById throws CustomerNotFoundException
    // → delete() never reaches deleteById
    when(customerRepository.findById(999L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> customerService.delete(999L))
        .isInstanceOf(CustomerNotFoundException.class);

    verify(customerRepository, never()).deleteById(any());
    verifyNoInteractions(eventPublisher);
  }

  @Test
  @DisplayName("Should throw CustomerServiceException when DataAccessException occurs during deletion")
  void shouldThrowCustomerServiceExceptionOnDataAccessException() {
    Long customerId = basicCustomer.id();
    when(customerRepository.findById(customerId)).thenReturn(Optional.of(basicEntity));
    doThrow(new DataAccessException("Database error") {})
        .when(customerRepository).deleteById(customerId);

    assertThatThrownBy(() -> customerService.delete(customerId))
        .isInstanceOf(CustomerServiceException.class);

    verify(customerRepository).findById(customerId);
    verify(customerRepository).deleteById(customerId);
    verifyNoInteractions(eventPublisher);
  }

  @Test
  @DisplayName("Should throw ServiceUnavailableException on QueryTimeoutException during deletion")
  void shouldThrowServiceUnavailableExceptionOnQueryTimeout() {
    Long customerId = basicCustomer.id();
    when(customerRepository.findById(customerId)).thenReturn(Optional.of(basicEntity));
    doThrow(new QueryTimeoutException("Database timeout"))
        .when(customerRepository).deleteById(customerId);

    assertThatThrownBy(() -> customerService.delete(customerId))
        .isInstanceOf(ServiceUnavailableException.class);

    verify(customerRepository).deleteById(customerId);
    verifyNoInteractions(eventPublisher);
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
  @DisplayName("Should handle unexpected exceptions gracefully in translateAndThrow")
  void shouldHandleUnexpectedExceptionsGracefully() {
    when(customerRepository.findById(basicCustomer.id())).thenReturn(Optional.of(basicEntity));
    doThrow(new RuntimeException("Unexpected error")).when(customerRepository).deleteById(basicCustomer.id());

    assertThatThrownBy(() -> customerService.delete(basicCustomer.id()))
        .isInstanceOf(CustomerServiceException.class)
        .hasMessageContaining("unexpected error");
  }

  // -----------------------------------------------------------------------
  // getCustomers (keyset pagination) tests
  // -----------------------------------------------------------------------

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

  // -----------------------------------------------------------------------
  // findByEmail tests
  // -----------------------------------------------------------------------

  @Test
  @DisplayName("findByEmail - repository returns value - returns mapped Customer")
  void findByEmail_repositoryReturnsValue_returnsMappedCustomer() throws Exception {
    CustomerEntity entity = CustomerEntity.builder()
        .id(basicCustomer.id())
        .customerJson(objectMapper.writeValueAsString(basicCustomer))
        .build();

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
  // Patch Tests
  // -----------------------------------------------------------------------

  @Test
  @DisplayName("patch() - applies firstName change only, other fields preserved")
  void shouldPatchFirstNameOnly() throws Exception {
    CustomerEntity entity = CustomerEntity.builder()
        .id(basicCustomer.id())
        .customerJson(objectMapper.writeValueAsString(basicCustomer))
        .build();

    when(customerRepository.findById(basicCustomer.id())).thenReturn(Optional.of(entity));
    when(customerRepository.saveAndFlush(any(CustomerEntity.class))).thenAnswer(i -> i.getArgument(0));

    Customer result = customerService.patch(basicCustomer.id(), "{\"firstName\":\"PatchedName\"}");

    assertThat(result.firstName()).isEqualTo("PatchedName");
    assertThat(result.lastName()).isEqualTo(basicCustomer.lastName());
    assertThat(result.type()).isEqualTo(basicCustomer.type());
    assertThat(result.emails()).isEqualTo(basicCustomer.emails());
    verify(eventPublisher).publishCustomerUpdated(any());
  }

  @Test
  @DisplayName("patch() - id in patch body is ignored; entity id is preserved")
  void shouldIgnoreIdInPatchBody() throws Exception {
    CustomerEntity entity = CustomerEntity.builder()
        .id(basicCustomer.id())
        .customerJson(objectMapper.writeValueAsString(basicCustomer))
        .build();

    when(customerRepository.findById(basicCustomer.id())).thenReturn(Optional.of(entity));
    when(customerRepository.saveAndFlush(any(CustomerEntity.class))).thenAnswer(i -> i.getArgument(0));

    String patchJson = "{\"id\": 999999, \"firstName\":\"PatchedName\"}";
    Customer result = customerService.patch(basicCustomer.id(), patchJson);

    assertThat(result.id()).isEqualTo(basicCustomer.id());
    assertThat(result.firstName()).isEqualTo("PatchedName");
  }

  @Test
  @DisplayName("patch() - applies nested emails change")
  void shouldPatchEmails() throws Exception {
    CustomerEntity entity = CustomerEntity.builder()
        .id(basicCustomer.id())
        .customerJson(objectMapper.writeValueAsString(basicCustomer))
        .build();

    when(customerRepository.findById(basicCustomer.id())).thenReturn(Optional.of(entity));
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
        .thenThrow(new EmptyResultDataAccessException("Customer not found", 1));

    assertThatThrownBy(() -> customerService.patch(999L, "{\"firstName\":\"X\"}"))
        .isInstanceOf(CustomerNotFoundException.class)
        .hasMessageContaining("Error retrieving customer");
  }

  @Test
  @DisplayName("patch() - malformed patch JSON throws IllegalArgumentException")
  void shouldThrowIllegalArgumentExceptionForMalformedPatchJson() throws Exception {
    CustomerEntity entity = CustomerEntity.builder()
        .id(basicCustomer.id())
        .customerJson(objectMapper.writeValueAsString(basicCustomer))
        .build();

    when(customerRepository.findById(basicCustomer.id())).thenReturn(Optional.of(entity));

    assertThatThrownBy(() -> customerService.patch(basicCustomer.id(), "{invalid json}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Failed to apply patch");
  }

  @Test
  @DisplayName("patch() - saveAndFlush failure throws CustomerServiceException and no event published")
  void shouldThrowServiceExceptionWhenPatchSaveFails() throws Exception {
    CustomerEntity entity = CustomerEntity.builder()
        .id(basicCustomer.id())
        .customerJson(objectMapper.writeValueAsString(basicCustomer))
        .build();

    when(customerRepository.findById(basicCustomer.id())).thenReturn(Optional.of(entity));
    when(customerRepository.saveAndFlush(any(CustomerEntity.class)))
        .thenThrow(new DataAccessException("DB write failure during patch") {});

    assertThatThrownBy(() -> customerService.patch(basicCustomer.id(), "{\"firstName\":\"X\"}"))
        .isInstanceOf(CustomerServiceException.class)
        .hasMessageContaining("Error patching customer");

    verify(customerRepository).saveAndFlush(any(CustomerEntity.class));
    verifyNoInteractions(eventPublisher);
  }

  @Test
  @DisplayName("Should throw IllegalArgumentException when patch nulls a required field")
  void shouldRejectPatchThatNullsRequiredField() throws Exception {
    Customer existing = CustomerTestDataProvider.createBasicCustomer();
    String existingJson = objectMapper.writeValueAsString(existing);
    CustomerEntity entity = CustomerEntity.builder()
        .id(existing.id())
        .createdAt(existing.createdAt())
        .updatedAt(existing.updatedAt())
        .customerJson(existingJson)
        .build();
    when(customerRepository.findById(existing.id())).thenReturn(Optional.of(entity));

    // PATCH nulls firstName — violates @NotBlank
    String patch = "{\"firstName\": null}";

    assertThatThrownBy(() -> customerService.patch(existing.id(), patch))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("firstName");
  }

}
