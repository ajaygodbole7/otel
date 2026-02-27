package org.observability.otel.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.observability.otel.domain.Customer;
import org.observability.otel.domain.CustomerTestDataProvider;
import org.observability.otel.exception.CustomerConflictException;
import org.observability.otel.exception.CustomerNotFoundException;
import org.observability.otel.exception.CustomerServiceException;
import org.observability.otel.exception.ServiceUnavailableException;
import org.observability.otel.rest.ExceptionTranslator;
import org.springframework.core.MethodParameter;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Tests for ExceptionTranslator's RFC 7807 Problem Detail responses
 */
@ExtendWith(MockitoExtension.class)
class ExceptionTranslatorUnitTest {

  @Mock private Environment env;
  @Mock private HttpServletRequest request;
  @Mock private MethodParameter methodParameter;

  private ExceptionTranslator exceptionTranslator;

  @BeforeEach
  void setUp() {
    exceptionTranslator = new ExceptionTranslator(env);

    // Complete request mock setup to avoid NPE
    when(request.getMethod()).thenReturn("POST");
    when(request.getRequestURI()).thenReturn("/api/v1/customers");
    when(request.getHeader("User-Agent")).thenReturn("TestAgent");
    when(request.getHeader("X-Request-Id")).thenReturn("test-123");
    when(request.getProtocol()).thenReturn("HTTP/1.1");
    when(request.getScheme()).thenReturn("https");
    when(request.isSecure()).thenReturn(true);
  }

  // Custom Exception Tests

  @Test
  @DisplayName("Should return 404 with Customer Not Found problem detail")
  void handleCustomerNotFound_ShouldReturn404() {
    // given
    Customer testCustomer = CustomerTestDataProvider.createBasicCustomer();
    CustomerNotFoundException ex = new CustomerNotFoundException("Customer " + testCustomer.id() + " not found");

    // when
    ResponseEntity<ProblemDetail> response = exceptionTranslator.handleCustomerNotFound(ex, request);

    // then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody().getTitle()).isEqualTo("Customer Not Found");
    assertThat(response.getBody().getStatus()).isEqualTo(404);
    assertThat(response.getBody().getDetail()).contains(testCustomer.id().toString());
  }

  @Test
  @DisplayName("Should return 409 with Customer Conflict problem detail")
  void handleCustomerConflict_ShouldReturn409() {
    // given
    CustomerConflictException ex = new CustomerConflictException("Customer already exists");

    // when
    ResponseEntity<ProblemDetail> response = exceptionTranslator.handleCustomerConflict(ex, request);

    // then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(response.getBody().getTitle()).isEqualTo("Customer Conflict");
    assertThat(response.getBody().getStatus()).isEqualTo(409);
  }

  @Test
  @DisplayName("Should return 503 with Service Unavailable problem detail")
  void handleServiceUnavailable_ShouldReturn503() {
    // given
    ServiceUnavailableException ex = new ServiceUnavailableException("Service is down");

    // when
    ResponseEntity<ProblemDetail> response = exceptionTranslator.handleServiceUnable(ex, request);

    // then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    assertThat(response.getBody().getTitle()).isEqualTo("Service Unavailable");
    assertThat(response.getBody().getStatus()).isEqualTo(503);
  }

  @Test
  @DisplayName("Should return 500 with Internal Error problem detail")
  void handleCustomerServiceException_ShouldReturn500() {
    // given
    CustomerServiceException ex = new CustomerServiceException("Internal error");

    // when
    ResponseEntity<ProblemDetail> response = exceptionTranslator.handleServiceGeneric(ex, request);

    // then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    assertThat(response.getBody().getTitle()).isEqualTo("Internal Error");
    assertThat(response.getBody().getStatus()).isEqualTo(500);
  }

  @Test
  @DisplayName("Should return 400 with Validation Error and field details")
  void handleMethodArgumentNotValid_ShouldReturn400() {
    // given
    Method dummyMethod = ExceptionTranslator.class.getDeclaredMethods()[0];
    when(methodParameter.getExecutable()).thenReturn(dummyMethod);

    BindingResult bindingResult = mock(BindingResult.class);
    FieldError fieldError = new FieldError(
        "customer", "type", null, false,
        new String[]{"NotNull"}, null, "Type is required"
    );
    when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError));

    MethodArgumentNotValidException ex = new MethodArgumentNotValidException(methodParameter, bindingResult);

    // when
    ResponseEntity<ProblemDetail> response = exceptionTranslator.handleValidationException(ex, request);

    // then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().getTitle()).isEqualTo("Validation Error");
    assertThat(response.getBody().getStatus()).isEqualTo(400);
  }

  // Spring Framework Exception Tests

  @Test
  @DisplayName("Should return 415 when Content-Type is not supported")
  void handleHttpMediaTypeNotSupported_ShouldReturn415() {
    // given
    HttpMediaTypeNotSupportedException ex = new HttpMediaTypeNotSupportedException(
        MediaType.APPLICATION_XML,
        List.of(MediaType.APPLICATION_JSON)
    );

    // when
    ResponseEntity<ProblemDetail> response = exceptionTranslator.handleMediaTypeNotSupported(ex, request);

    // then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
    assertThat(response.getBody().getTitle()).isEqualTo("UNSUPPORTED_MEDIA_TYPE");
    assertThat(response.getBody().getStatus()).isEqualTo(415);
  }

  @Test
  @DisplayName("Should return 406 when Accept header cannot be satisfied")
  void handleHttpMediaTypeNotAcceptable_ShouldReturn406() {
    // given
    HttpMediaTypeNotAcceptableException ex = new HttpMediaTypeNotAcceptableException(
        List.of(MediaType.APPLICATION_JSON)
    );

    // when
    ResponseEntity<ProblemDetail> response = exceptionTranslator.handleMediaTypeNotAccepted(ex, request);

    // then — 406 Not Acceptable, not 415 Unsupported Media Type
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_ACCEPTABLE);
    assertThat(response.getBody().getTitle()).isEqualTo("NOT_ACCEPTABLE");
    assertThat(response.getBody().getStatus()).isEqualTo(406);
  }

  @Test
  @DisplayName("Should return 405 when HTTP method is not supported")
  void handleHttpRequestMethodNotSupported_ShouldReturn405() {
    // given
    HttpRequestMethodNotSupportedException ex = new HttpRequestMethodNotSupportedException(
        "PATCH",
        List.of("GET", "POST", "PUT", "DELETE")
    );

    // when
    ResponseEntity<ProblemDetail> response = exceptionTranslator.handleMethodNotSupported(ex, request);

    // then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
    assertThat(response.getBody().getTitle()).isEqualTo("METHOD_NOT_ALLOWED");
    assertThat(response.getBody().getStatus()).isEqualTo(405);
  }

  @Test
  @DisplayName("Should return 400 with parameter details when required parameter is missing")
  void handleMissingServletRequestParameter_ShouldReturn400() {
    // given
    MissingServletRequestParameterException ex = new MissingServletRequestParameterException(
        "page",
        "integer"
    );

    // when
    ResponseEntity<ProblemDetail> response = exceptionTranslator.handleMissingParams(ex, request);

    // then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().getTitle()).isEqualTo("Missing Parameter");
    assertThat(response.getBody().getStatus()).isEqualTo(400);
    assertThat(response.getBody().getProperties().get("parameter")).isEqualTo("page");
    assertThat(response.getBody().getProperties().get("parameterType")).isEqualTo("integer");
  }

  @Test
  @DisplayName("Should return 400 with type details when path variable type is invalid")
  void handleMethodArgumentTypeMismatch_ShouldReturn400() {
    // given
    MethodArgumentTypeMismatchException ex = new MethodArgumentTypeMismatchException(
        "abc",
        Long.class,
        "id",
        methodParameter,
        new NumberFormatException()
    );

    // when
    ResponseEntity<ProblemDetail> response = exceptionTranslator.handleTypeMismatch(ex, request);

    // then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().getTitle()).isEqualTo("Invalid Path Variable");
    assertThat(response.getBody().getStatus()).isEqualTo(400);
    assertThat(response.getBody().getProperties().get("parameter")).isEqualTo("id");
    assertThat(response.getBody().getProperties().get("expectedType")).isEqualTo("Long");
  }

  @Test
  @DisplayName("Should return 400 with Malformed JSON problem detail")
  void handleHttpMessageNotReadable_ShouldReturn400() {
    // given
    HttpMessageNotReadableException ex = new HttpMessageNotReadableException("Malformed JSON");

    // when
    ResponseEntity<ProblemDetail> response = exceptionTranslator.handleJsonParseError(ex, request);

    // then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().getTitle()).isEqualTo("Malformed JSON");
    assertThat(response.getBody().getStatus()).isEqualTo(400);
  }

  @Test
  @DisplayName("Should return 500 for unexpected exceptions")
  void handleGenericException_ShouldReturn500() {
    // given
    Exception ex = new RuntimeException("Unexpected error");

    // when
    ResponseEntity<ProblemDetail> response = exceptionTranslator.handleUnexpectedException(ex, request);

    // then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    assertThat(response.getBody().getTitle()).isEqualTo("An unexpected error occurred");
    assertThat(response.getBody().getStatus()).isEqualTo(500);
  }
}
