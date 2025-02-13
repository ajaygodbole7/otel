package org.observability.otel.unit;


import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.OngoingStubbing;
import org.observability.otel.domain.Customer;
import org.observability.otel.domain.CustomerTestDataProvider;
import org.observability.otel.exception.CustomerConflictException;
import org.observability.otel.exception.CustomerNotFoundException;
import org.observability.otel.exception.CustomerServiceException;
import org.observability.otel.exception.ServiceUnavailableException;
import org.observability.otel.rest.ExceptionTranslator;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.core.MethodParameter;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.MethodValidationResult;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Tests for ExceptionTranslator's RFC 7807 Problem Detail responses
 */
@ExtendWith(MockitoExtension.class)
class ExceptionTranslatorTest {

  @Mock private Environment env;
  @Mock private HttpServletRequest request;
  @Mock private BindingResult bindingResult;
  @Mock private MethodParameter methodParameter;

  private ExceptionTranslator exceptionTranslator;
  private Customer testCustomer;

  @BeforeEach
  void setUp() {
    exceptionTranslator = new ExceptionTranslator(env);
    testCustomer = CustomerTestDataProvider.createBasicCustomer();

    // Complete request mock setup to avoid NPE
    when(request.getRemoteAddr()).thenReturn("127.0.0.1");
    when(request.getMethod()).thenReturn("POST");
    when(request.getRequestURI()).thenReturn("/api/v1/customers");
    when(request.getHeader("User-Agent")).thenReturn("TestAgent");
    when(request.getHeader("X-Request-Id")).thenReturn("test-123");
    when(request.getHeader("X-Forwarded-For")).thenReturn("");
    lenient().when(request.getProtocol()).thenReturn("HTTP/1.1");
    lenient().when(request.getScheme()).thenReturn("https");
    lenient().when(request.isSecure()).thenReturn(true);


  }

  // Custom Exception Tests
  @Test
  void handleCustomerNotFound_ShouldReturn404() {
    // given
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
  void handleMethodArgumentNotValid_ShouldReturn400() {
    // given
    Method dummyMethod = ExceptionTranslator.class.getDeclaredMethods()[0]; // get any method from the class
    lenient().when(methodParameter.getParameterType()).thenReturn((Class) Customer.class);
    lenient().when(methodParameter.getExecutable()).thenReturn(dummyMethod);
    lenient().when(methodParameter.getParameterName()).thenReturn("customer");

    FieldError fieldError = new FieldError(
        "customer", "type", null, false,
        new String[]{"NotNull"}, null, "Type is required"
    );
    lenient().when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError));
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
  void handleHttpMediaTypeNotAcceptable_ShouldReturn406() {
    // given
    HttpMediaTypeNotAcceptableException ex = new HttpMediaTypeNotAcceptableException(
        List.of(MediaType.APPLICATION_JSON)
    );

    // when
    ResponseEntity<ProblemDetail> response = exceptionTranslator.handleMediaTypeNotAccepted(ex, request);

    // then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
    assertThat(response.getBody().getTitle()).isEqualTo("UNSUPPORTED_MEDIA_TYPE");
    assertThat(response.getBody().getStatus()).isEqualTo(415);
  }

  @Test
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
