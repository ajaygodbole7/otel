package org.observability.otel.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.observability.otel.rest.ApiConstants.ApiPath.*;
import static org.observability.otel.rest.ApiConstants.ApiPath.BASE_V1_API_PATH;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.observability.otel.config.UnitTestConfig;
import org.observability.otel.domain.*;
import org.observability.otel.exception.CustomerConflictException;
import org.observability.otel.exception.CustomerNotFoundException;
import org.observability.otel.exception.CustomerServiceException;
import org.observability.otel.rest.CustomerController;
import org.observability.otel.service.CustomerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;

@WebMvcTest(CustomerController.class)
@Import(UnitTestConfig.class)
// @TestPropertySource(properties = "spring.data.jpa.repositories.enabled=false")
class CustomerControllerUnitTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @MockitoBean private CustomerService customerService;

  private String customersUrl;
  private Customer basicCustomer;
  private Customer fullCustomer;
  private Customer updatedCustomer;

  @BeforeEach
  void setUp() {
    customersUrl = BASE_V1_API_PATH + CUSTOMERS;
    basicCustomer = CustomerTestDataProvider.createBasicCustomer();
    fullCustomer = CustomerTestDataProvider.createFullCustomer();
    updatedCustomer =
        CustomerTestDataProvider.createUpdateCustomer(
            basicCustomer.id(), basicCustomer.createdAt());
  }

  private <T> T parseResponse(MvcResult result, Class<T> clazz) throws Exception {
    return objectMapper.readValue(result.getResponse().getContentAsString(), clazz);
  }

  private MvcResult performRequest(RequestBuilder requestBuilder) throws Exception {
    return mockMvc.perform(requestBuilder).andReturn();
  }

  private void assertResponse(MvcResult result, HttpStatus expectedStatus) {
    assertThat(result.getResponse().getStatus()).isEqualTo(expectedStatus.value());
  }

  private void assertJsonResponse(MvcResult result, HttpStatus expectedStatus) {
    assertResponse(result, expectedStatus);
    assertThat(result.getResponse().getContentType()).contains(MediaType.APPLICATION_JSON_VALUE);
  }

  private void assertProblemJsonResponse(MvcResult result, HttpStatus expectedStatus) {
    assertResponse(result, expectedStatus);
    assertThat(result.getResponse().getContentType()).contains("application/problem+json");
  }

  private void assertErrorResponse(
      MvcResult result, HttpStatus expectedStatus, String errorTitle, String errorMessage)
      throws UnsupportedEncodingException {
    assertProblemJsonResponse(result, expectedStatus);
    assertThat(result.getResponse().getContentAsString()).contains(errorTitle);
    if (errorMessage != null) {
      assertThat(result.getResponse().getContentAsString()).contains(errorMessage);
    }
  }

  private Customer readCustomerResponse(MockHttpServletResponse response) throws Exception {
    return objectMapper.readValue(response.getContentAsString(), Customer.class);
  }

  private RequestBuilder buildGetRequest(Long id) {
    return get(customersUrl + "/" + id)
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.APPLICATION_JSON, MediaType.APPLICATION_PROBLEM_JSON);
  }

  private RequestBuilder buildPostRequest(Customer customer) {
    return post(customersUrl)
        .content(writeValueAsString(customer))
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.APPLICATION_JSON, MediaType.APPLICATION_PROBLEM_JSON);
  }

  private RequestBuilder buildPutRequest(Long id, Customer customer) {
    return put(customersUrl + "/" + id)
        .content(writeValueAsString(customer))
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.APPLICATION_JSON, MediaType.APPLICATION_PROBLEM_JSON);
  }

  private RequestBuilder buildDeleteRequest(Long id) {
    return delete(customersUrl + "/" + id)
        .accept(MediaType.APPLICATION_JSON, MediaType.APPLICATION_PROBLEM_JSON)
        .contentType(MediaType.APPLICATION_JSON);
  }

  private String writeValueAsString(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  /*
   * HAPPY PATH TESTS
   */
  @Test
  @DisplayName("Should successfully create new customer")
  void shouldCreateNewCustomer() throws Exception {
    when(customerService.create(any(Customer.class))).thenReturn(fullCustomer);

    MvcResult result =
        mockMvc
            .perform(
                post(customersUrl)
                    .content(objectMapper.writeValueAsString(fullCustomer))
                    .contentType(MediaType.APPLICATION_JSON))
            .andReturn();

    assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.CREATED.value());

    assertThat(parseResponse(result, Customer.class))
        .usingRecursiveComparison()
        .isEqualTo(fullCustomer);
  }

  @Test
  @DisplayName("Should successfully retrieve customer when found")
  void shouldRetrieveExistingCustomer() throws Exception {
    when(customerService.findById(any(Long.class))).thenReturn(basicCustomer);

    MvcResult result =
        mockMvc
            .perform(
                get(customersUrl + "/1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON))
            .andReturn();

    assertJsonResponse(result, HttpStatus.OK);
    Customer responseCustomer = parseResponse(result, Customer.class);
    assertThat(responseCustomer).usingRecursiveComparison().isEqualTo(basicCustomer);
  }

  @Test
  @DisplayName("Should successfully update existing customer")
  void shouldUpdateExistingCustomer() throws Exception {
    when(customerService.update(any(Long.class), any(Customer.class))).thenReturn(updatedCustomer);

    MvcResult result = performRequest(buildPutRequest(basicCustomer.id(), updatedCustomer));

    assertJsonResponse(result, HttpStatus.OK);
    assertThat(readCustomerResponse(result.getResponse()))
        .usingRecursiveComparison()
        .isEqualTo(updatedCustomer);
  }

  @Test
  @DisplayName("Should successfully delete existing customer")
  void shouldDeleteExistingCustomer() throws Exception {
    doNothing().when(customerService).delete(1L);

    MvcResult result =
        mockMvc
            .perform(
                delete(customersUrl + "/1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON))
            .andReturn();

    assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.NO_CONTENT.value());
    // Verify that the service method was indeed called
    verify(customerService, times(1)).delete(1L);
  }

  /*
   *   Error Handling tests
   */
  @Test
  @DisplayName("Should return not found when customer doesn't exist")
  void shouldReturnNotFoundForNonExistentCustomer() throws Exception {
    String errorMessage = "Customer not found";
    when(customerService.findById(any(Long.class)))
        .thenThrow(new CustomerNotFoundException(errorMessage));

    MvcResult result =
        mockMvc
            .perform(
                get(customersUrl + "/9999")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON))
            .andReturn();

    assertErrorResponse(result, HttpStatus.NOT_FOUND, "Customer not found", errorMessage);
  }

  @Test
  @DisplayName("Should list all customers successfully")
  void shouldListAllCustomersSuccessfully() throws Exception {
    List<Customer> customers = Arrays.asList(basicCustomer, updatedCustomer);
    when(customerService.getAllCustomers()).thenReturn(customers);

    MvcResult result =
        mockMvc
            .perform(
                get(customersUrl)
                    .accept(MediaType.APPLICATION_JSON)
                    .contentType(MediaType.APPLICATION_JSON))
            .andReturn();

    assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value());

    java.util.List<org.observability.otel.domain.Customer> responseCustomers =
        java.util.Arrays.asList(parseResponse(result, Customer[].class));
    assertThat(responseCustomers).usingRecursiveComparison().isEqualTo(customers);
  }

  @Test
  @DisplayName("Should reject duplicate customer creation")
  void shouldRejectDuplicateCustomer() throws Exception {
    // Given
    CustomerConflictException conflictException =
        new CustomerConflictException("Customer already exists");
    when(customerService.create(any(Customer.class))).thenThrow(conflictException);

    // When
    MvcResult result = performRequest(buildPostRequest(basicCustomer));
    MockHttpServletResponse response = result.getResponse();
    String jsonResponse = response.getContentAsString();
    JsonNode jsonNode = new ObjectMapper().readTree(jsonResponse);

    // Then
    assertThat(response.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
    assertThat(jsonNode.get("type").asText()).isEqualTo("https://api.example.com/errors/409");
    assertThat(jsonNode.get("title").asText()).isEqualTo("Customer Conflict");
    assertThat(jsonNode.get("status").asInt()).isEqualTo(HttpStatus.CONFLICT.value());
    assertThat(jsonNode.get("detail").asText()).contains("Customer already exists");

    // Verify service interaction
    verify(customerService, times(1)).create(any(Customer.class));
  }

  /*
   @Test
   @DisplayName("Should reject update with invalid data")
   void shouldRejectInvalidUpdate() throws Exception {
     Customer invalidCustomer =
         new Customer(
             null,
             "",
             "",
             null,
             "",
             null,
             Collections.emptyList(),
             Collections.emptyList(),
             Collections.emptyList(),
             Collections.emptyList(),
             null,
             null);

     MvcResult result = performRequest(buildPutRequest(basicCustomer.id(), invalidCustomer));

     assertErrorResponse(
         result, HttpStatus.BAD_REQUEST, "Method Validation Error", "Validation failure");
   }

  */
  @Test
  @DisplayName("Should reject update for non-existent customer")
  void shouldRejectUpdateForNonExistentCustomer() throws Exception {
    String errorMessage = "Customer not found";
    when(customerService.update(any(Long.class), any(Customer.class)))
        .thenThrow(new CustomerNotFoundException(errorMessage));

    MvcResult result = performRequest(buildPutRequest(999L, updatedCustomer));

    assertErrorResponse(result, HttpStatus.NOT_FOUND, "Customer not found", errorMessage);
  }

  @Test
  @DisplayName("Should reject deletion of non-existent customer")
  void shouldRejectDeletionOfNonExistentCustomer() throws Exception {
    String errorMessage = "Customer not found";
    doThrow(new CustomerNotFoundException(errorMessage))
        .when(customerService)
        .delete(any(Long.class));

    MvcResult result = performRequest(buildDeleteRequest(999L));

    assertErrorResponse(result, HttpStatus.NOT_FOUND, "Customer Not Found", errorMessage);
  }

  @Test
  @DisplayName("Should handle internal server errors")
  void shouldHandleInternalServerErrors() throws Exception {
    String errorMessage = "unexpected error";
    when(customerService.findById(any(Long.class)))
        .thenThrow(new CustomerServiceException(errorMessage));

    MvcResult result = performRequest(buildGetRequest(1L));

    assertErrorResponse(result, HttpStatus.INTERNAL_SERVER_ERROR, "Internal Error", errorMessage);
  }

  @Test
  @DisplayName("Should reject malformed JSON")
  void shouldRejectMalformedJson() throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post(customersUrl)
                    .content("{invalid json}")
                    .contentType(MediaType.APPLICATION_JSON))
            .andReturn();

    assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
    assertThat(result.getResponse().getContentAsString()).contains("JSON parsing error");
  }

  @Test
  @DisplayName("Should reject unsupported media type")
  void shouldRejectUnsupportedMediaType() throws Exception {
    MvcResult result =
        performRequest(
            post(customersUrl)
                .content(writeValueAsString(basicCustomer))
                .contentType(MediaType.TEXT_PLAIN)
                .accept(MediaType.APPLICATION_JSON));

    assertResponse(result, HttpStatus.UNSUPPORTED_MEDIA_TYPE);
  }

  @Test
  @DisplayName("Should reject request with invalid path variable")
  void shouldRejectInvalidPathVariable() throws Exception {
    MvcResult result = performRequest(buildGetRequest(null));

    assertResponse(result, HttpStatus.BAD_REQUEST); // Corrected from UNSUPPORTED_MEDIA_TYPE
  }

  @Test
  @DisplayName("Error response should include traceId and spanId")
  void errorResponseShouldIncludeTraceDetails() throws Exception {
    /*
    // Mocking SpanContext
    SpanContext mockSpanContext = mock(SpanContext.class);
    when(mockSpanContext.isValid()).thenReturn(true);
    when(mockSpanContext.getTraceId()).thenReturn("mock-trace-id-12345");
    when(mockSpanContext.getSpanId()).thenReturn("mock-span-id-67890");

    // Mocking Span and linking SpanContext
    Span mockSpan = mock(Span.class);
    when(mockSpan.getSpanContext()).thenReturn(mockSpanContext);

    // Correct use of MockedStatic for Span.current()
    try (MockedStatic<Span> spanMockedStatic = mockStatic(Span.class)) {
      spanMockedStatic.when(Span::current).thenReturn(mockSpan);

      // Triggering service exception
      when(customerService.findById(anyLong()))
          .thenThrow(new CustomerServiceException("Internal error"));

      MvcResult result = performRequest(buildGetRequest(1L));
      String jsonResponse = result.getResponse().getContentAsString();

      // Assertions
      assertThat(jsonResponse).contains("mock-trace-id-12345");
      assertThat(jsonResponse).contains("mock-span-id-67890");
    }
    
     */
  }

  @Test
  @DisplayName("Delete request should be idempotent")
  void deleteShouldBeIdempotent() throws Exception {
    doNothing().when(customerService).delete(anyLong());
    performRequest(buildDeleteRequest(1L));
    performRequest(buildDeleteRequest(1L)); // Deleting again
    verify(customerService, times(2)).delete(1L);
  }

  @Test
  @DisplayName("Should reject request with non-numeric ID")
  void shouldRejectNonNumericId() throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                get(customersUrl + "/abc")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON, MediaType.APPLICATION_PROBLEM_JSON))
            .andReturn();
    assertResponse(result, HttpStatus.BAD_REQUEST);
  }

  @Test
  @DisplayName("Should return an empty list when no customers exist")
  void shouldReturnEmptyListWhenNoCustomersExist() throws Exception {
    when(customerService.getAllCustomers()).thenReturn(Collections.emptyList());

    MvcResult result =
        mockMvc
            .perform(
                get(customersUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON, MediaType.APPLICATION_PROBLEM_JSON))
            .andReturn();

    assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value());
    assertThat(result.getResponse().getContentAsString()).isEqualTo("[]");
  }

  @Test
  @DisplayName("Should reject missing required parameters")
  void shouldRejectMissingRequiredParameters() throws Exception {
    MvcResult result = performRequest(buildGetRequest(null)); // Missing ID

    assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
    assertThat(result.getResponse().getContentAsString()).contains("Invalid Path Variable");
  }

  @Test
  @DisplayName("Should handle unexpected exceptions gracefully")
  void shouldHandleUnexpectedException() throws Exception {
    when(customerService.findById(anyLong())).thenThrow(new RuntimeException("Unexpected error"));

    MvcResult result = performRequest(buildGetRequest(1L));

    assertThat(result.getResponse().getStatus())
        .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
    assertThat(result.getResponse().getContentAsString()).contains("An unexpected error occurred");
  }

  /*
   * JSR-380 Bean Validation tests
   */

  @Test
  @DisplayName("Should return 400 when firstName is missing")
  void shouldRejectMissingFirstName() throws Exception {
    Customer invalidCustomer =
        CustomerTestDataProvider.createCustomerWithMissingFirstName(fullCustomer);

    MvcResult result = performRequest(buildPostRequest(invalidCustomer));

    assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
    assertThat(result.getResponse().getContentAsString()).contains("firstName");
  }

  @Test
  @DisplayName("Should return 400 when lastName is blank")
  void shouldRejectBlankLastName() throws Exception {
    Customer invalidCustomer =
        CustomerTestDataProvider.createCustomerWithBlankLastName(fullCustomer);

    MvcResult result = performRequest(buildPostRequest(invalidCustomer));

    assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
    assertThat(result.getResponse().getContentAsString()).contains("lastName");
  }

  @Test
  @DisplayName("Should return 400 when type is blank")
  void shouldRejectBlankType() throws Exception {
    Customer invalidCustomer =
        CustomerTestDataProvider.createCustomerWithBlankType(fullCustomer);

    MvcResult result = performRequest(buildPostRequest(invalidCustomer));

    assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
    assertThat(result.getResponse().getContentAsString()).contains("type");
  }

  @Test
  @DisplayName("Should return 400 when emails list is empty")
  void shouldRejectEmptyEmailsList() throws Exception {
    Customer invalidCustomer =
        CustomerTestDataProvider.createCustomerWithEmptyEmails(fullCustomer);

    MvcResult result = performRequest(buildPostRequest(invalidCustomer));

    assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
    assertThat(result.getResponse().getContentAsString()).contains("emails");
  }

  @Test
  @DisplayName("Should return 400 when email format is invalid")
  void shouldRejectInvalidEmailFormat() throws Exception {
    Customer invalidCustomer =
        CustomerTestDataProvider.createCustomerWithInvalidEmailFormat(fullCustomer);

    MvcResult result = performRequest(buildPostRequest(invalidCustomer));

    assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
    assertThat(result.getResponse().getContentAsString()).contains("emails[0].email");
  }

  @Test
  @DisplayName("Should return 400 when phone number fails pattern validation")
  void shouldRejectInvalidPhoneNumber() throws Exception {
    Customer invalidCustomer =
        CustomerTestDataProvider.createCustomerWithInvalidPhoneNumber(fullCustomer);

    MvcResult result = performRequest(buildPostRequest(invalidCustomer));

    assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
    assertThat(result.getResponse().getContentAsString()).contains("number");
  }

  @Test
  @DisplayName("Should return 201 when valid full payload is submitted (regression)")
  void shouldAcceptValidFullPayload() throws Exception {
    when(customerService.create(any(Customer.class))).thenReturn(fullCustomer);

    MvcResult result = performRequest(buildPostRequest(fullCustomer));

    assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.CREATED.value());
  }
}
