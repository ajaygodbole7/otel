package org.observability.otel.rest;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.observability.otel.exception.CustomerConflictException;
import org.observability.otel.exception.CustomerNotFoundException;
import org.observability.otel.exception.CustomerServiceException;
import org.observability.otel.exception.ServiceUnavailableException;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.core.MethodParameter;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Global exception handler that translates exceptions into RFC 7807 Problem Details. Integrates
 * with OpenTelemetry for error tracking and distributed tracing.
 */
@RestControllerAdvice
@Slf4j
@RequiredArgsConstructor
@RequestMapping(
    produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE})
public class ExceptionTranslator {

  private static final String ERROR_BASE_URL = "https://api.example.com/errors/";
  private static final String ERROR_CODE = "errorCode";
  private static final String TIMESTAMP = "timestamp";
  private static final String TRACE_ID = "traceId";
  private static final String SPAN_ID = "spanId";
  private static final String REQUEST_ID = "requestId";
  private final Environment env;

  /** Handle Custom Exceptions defined in the Service layer */
  @ExceptionHandler(CustomerNotFoundException.class)
  public ResponseEntity<ProblemDetail> handleCustomerNotFound(
      RuntimeException ex, HttpServletRequest request) {
    return buildErrorResponse(HttpStatus.NOT_FOUND, "Customer Not Found", ex, request);
  }

  @ExceptionHandler(CustomerConflictException.class)
  public ResponseEntity<ProblemDetail> handleCustomerConflict(
      RuntimeException ex, HttpServletRequest request) {
    return buildErrorResponse(HttpStatus.CONFLICT, "Customer Conflict", ex, request);
  }

  @ExceptionHandler(ServiceUnavailableException.class)
  public ResponseEntity<ProblemDetail> handleServiceUnable(
      RuntimeException ex, HttpServletRequest request) {
    return buildErrorResponse(HttpStatus.SERVICE_UNAVAILABLE, "Service Unavailable", ex, request);
  }

  @ExceptionHandler(CustomerServiceException.class)
  public ResponseEntity<ProblemDetail> handleServiceGeneric(
      RuntimeException ex, HttpServletRequest request) {
    return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Error", ex, request);
  }

  /** GENERIC SPRING EXCEPTIONS */

  /**
   * Handles validation errors when request body or parameters are invalid. Captures details such as
   * field name, rejected value, error code, and binding failure status.
   */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ProblemDetail> handleValidationException(
      MethodArgumentNotValidException ex, HttpServletRequest request) {
    ProblemDetail problemDetail =
        createBaseProblemDetail(HttpStatus.BAD_REQUEST, "Validation Error", ex, request);
    problemDetail.setProperty(
        "errors",
        ex.getBindingResult().getFieldErrors().stream()
            .map(
                error ->
                    Map.of(
                        "field", Optional.ofNullable(error.getField()).orElse("unknown"),
                        "objectName", Optional.ofNullable(error.getObjectName()).orElse("N/A"),
                        "rejectedValue",
                            Optional.ofNullable(error.getRejectedValue())
                                .map(Object::toString)
                                .orElse("null"),
                        "message",
                            Optional.ofNullable(error.getDefaultMessage()).orElse("No message"),
                        "errorCode", Optional.ofNullable(error.getCode()).orElse("UNKNOWN_CODE"),
                        "bindingFailure", String.valueOf(error.isBindingFailure())))
            .collect(Collectors.toList()));
    return ResponseEntity.badRequest().body(problemDetail);
  }

  /** Handles JSON parsing errors when the request body is malformed. */
  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ProblemDetail> handleJsonParseError(
      HttpMessageNotReadableException ex, HttpServletRequest request) {
    ProblemDetail problemDetail =
        createBaseProblemDetail(HttpStatus.BAD_REQUEST, "Malformed JSON", ex, request);
    // Extract the detailed error information about the JSON parsing issue
    String errorDetail =
        Optional.ofNullable(ex.getMostSpecificCause())
            .map(cause -> "JSON parsing error: " + cause.getMessage())
            .orElse("Malformed JSON input: " + ex.getMessage());

    problemDetail.setDetail(errorDetail);
    return ResponseEntity.badRequest().body(problemDetail);
  }

  /** Handles constraint violations from bean validation annotations like @NotNull, @Size, etc. */
  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<ProblemDetail> handleConstraintViolation(
      ConstraintViolationException ex, HttpServletRequest request) {
    return buildErrorResponse(HttpStatus.BAD_REQUEST, "Constraint Violation", ex, request);
  }

  /** Handles illegal argument exceptions often used for precondition checks. */
  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ProblemDetail> handleIllegalArgument(
      IllegalArgumentException ex, HttpServletRequest request) {
    return buildErrorResponse(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", ex, request);
  }

  /** Handles unsupported HTTP methods (e.g., using POST when only GET is allowed). */
  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  public ResponseEntity<ProblemDetail> handleMethodNotSupported(
      HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
    return buildErrorResponse(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", ex, request);
  }

  /** Handles unsupported content types (e.g., XML when JSON is expected). */
  @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
  public ResponseEntity<ProblemDetail> handleMediaTypeNotSupported(
      HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {
    return buildErrorResponse(
        HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE", ex, request);
  }

  /** Handles requests where the server cannot produce a response matching the client's Accept header. */
  @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
  public ResponseEntity<ProblemDetail> handleMediaTypeNotAccepted(
      HttpMediaTypeNotAcceptableException ex, HttpServletRequest request) {
    return buildErrorResponse(
        HttpStatus.NOT_ACCEPTABLE, "NOT_ACCEPTABLE", ex, request);
  }

  /** Handles missing required request parameters and specifies which parameter was missing. */
  @ExceptionHandler(MissingServletRequestParameterException.class)
  public ResponseEntity<ProblemDetail> handleMissingParams(
      MissingServletRequestParameterException ex, HttpServletRequest request) {
    ProblemDetail problemDetail =
        createBaseProblemDetail(HttpStatus.BAD_REQUEST, "Missing Parameter", ex, request);
    problemDetail.setProperty("parameter", ex.getParameterName());
    problemDetail.setProperty("parameterType", ex.getParameterType());
    return ResponseEntity.badRequest().body(problemDetail);
  }

  @ExceptionHandler(HandlerMethodValidationException.class)
  public ResponseEntity<ProblemDetail> handleMethodValidation(
      HandlerMethodValidationException ex, HttpServletRequest request) {

    ProblemDetail problemDetail =
        createBaseProblemDetail(HttpStatus.BAD_REQUEST, "Method Validation Error", ex, request);

    List<Map<String, Object>> errors =
        ex.getParameterValidationResults().stream()
            .map(
                result -> {
                  Map<String, Object> errorDetails = new HashMap<>();
                  MethodParameter param = result.getMethodParameter();

                  // Parameter details
                  errorDetails.put(
                      "parameter", Optional.ofNullable(param.getParameterName()).orElse("unknown"));
                  errorDetails.put("type", param.getParameterType().getSimpleName());

                  // Multiple error messages
                  List<String> messages =
                      result.getResolvableErrors().stream()
                          .map(MessageSourceResolvable::getDefaultMessage)
                          .toList();
                  errorDetails.put("messages", messages);

                  // Add error code if available
                  result.getResolvableErrors().stream()
                      .map(MessageSourceResolvable::getCodes)
                      .filter(Objects::nonNull)
                      .flatMap(Arrays::stream)
                      .findFirst()
                      .ifPresent(code -> errorDetails.put("errorCode", code));

                  // Explicit annotation detection
                  Optional<String> annotation =
                      Arrays.stream(param.getParameterAnnotations())
                          .map(
                              a -> {
                                if (a.annotationType().equals(RequestParam.class))
                                  return "RequestParam";
                                if (a.annotationType().equals(RequestBody.class))
                                  return "RequestBody";
                                if (a.annotationType().equals(PathVariable.class))
                                  return "PathVariable";
                                return null;
                              })
                          .filter(Objects::nonNull)
                          .findFirst();
                  errorDetails.put("annotation", annotation.orElse("N/A"));

                  return errorDetails;
                })
            .toList();

    problemDetail.setProperty("validationErrors", errors);

    // Handle cross-parameter validation errors
    if (!ex.getCrossParameterValidationResults().isEmpty()) {
      List<String> crossErrors =
          ex.getCrossParameterValidationResults().stream()
              .map(MessageSourceResolvable::getDefaultMessage)
              .toList();
      problemDetail.setProperty("crossParameterErrors", crossErrors);
    }

    return ResponseEntity.status(ex.getStatusCode()).body(problemDetail);
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<ProblemDetail> handleTypeMismatch(
      MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
    ProblemDetail problemDetail =
        createBaseProblemDetail(HttpStatus.BAD_REQUEST, "Invalid Path Variable", ex, request);

    problemDetail.setProperty("parameter", ex.getName());
    problemDetail.setProperty(
        "expectedType",
        ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "Unknown");
    problemDetail.setProperty("invalidValue", ex.getValue());

    return ResponseEntity.badRequest().body(problemDetail);
  }

  /**
   * Catch-all handler for all other exceptions not defined in this @ControllerAdvice, returning a
   * 500 Internal Server Error.
   */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ProblemDetail> handleUnexpectedException(
      Exception ex, HttpServletRequest request) {
    log.error("Unexpected error occurred", ex);
    return buildErrorResponse(
        HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", ex, request);
  }

  private ResponseEntity<ProblemDetail> buildErrorResponse(
      HttpStatus status, String title, Exception ex, HttpServletRequest request) {
    ProblemDetail problemDetail = createBaseProblemDetail(status, title, ex, request);
    return ResponseEntity.status(status).body(problemDetail);
  }

  /** Creates the ProblemDetail object with standard properties like status, timestamp, etc. */
  private ProblemDetail createBaseProblemDetail(
      HttpStatus status, String title, Exception ex, HttpServletRequest request) {
    ProblemDetail problemDetail = ProblemDetail.forStatus(status);
    problemDetail.setTitle(title);
    problemDetail.setDetail(ex.getMessage());
    problemDetail.setType(URI.create(ERROR_BASE_URL + status.value()));
    problemDetail.setInstance(URI.create(request.getRequestURI()));
    problemDetail.setProperty(ERROR_CODE, title.toUpperCase().replace(" ", "_"));
    problemDetail.setProperty(TIMESTAMP, Instant.now());

    addDebugInfo(problemDetail, ex);
    addRequestMetadata(problemDetail, request);
    markSpanError(problemDetail, ex);

    return problemDetail;
  }

  /** Adds debug information like stack traces in development environments. */
  private void addDebugInfo(ProblemDetail detail, Exception ex) {
    final int MAX_STACK_TRACE_LENGTH = 5000; // ~50 lines of stack trace
    if (detail == null || ex == null) return;
    if (env.acceptsProfiles(Profiles.of("dev"))) {
      detail.setProperty("exception", ex.getClass().getName());
      String fullStackTrace = ExceptionUtils.getStackTrace(ex);
      String truncatedStackTrace =
          fullStackTrace.length() > MAX_STACK_TRACE_LENGTH
              ? fullStackTrace.substring(0, MAX_STACK_TRACE_LENGTH) + "..."
              : fullStackTrace;
      detail.setProperty("stackTrace", truncatedStackTrace);
    }
  }

  /** Adds both common and optional metadata to the ProblemDetail. */
  private void addRequestMetadata(ProblemDetail detail, HttpServletRequest request) {
    Map<String, String> metadata =
        Map.of(
            "clientIp", request.getRemoteAddr(),
            "httpMethod", request.getMethod(),
            "requestPath", request.getRequestURI(),
            "userAgent", Optional.ofNullable(request.getHeader("User-Agent")).orElse(""),
            "requestId", Optional.ofNullable(request.getHeader("X-Request-Id")).orElse(""),
            "forwardedFor", Optional.ofNullable(request.getHeader("X-Forwarded-For")).orElse(""),
            "protocol", request.getProtocol(),
            "scheme", request.getScheme(),
            "isSecure", String.valueOf(request.isSecure()));
    detail.setProperty("request", metadata);
  }

  /** Marks the current span as an error in OpenTelemetry for better trace analysis. */
  private void markSpanError(ProblemDetail detail, Exception ex) {
    /*
    Span span = Span.current();
    if (span.getSpanContext().isValid()) {
      span.setStatus(StatusCode.ERROR);
      span.setAttribute("error.message", ex.getMessage());
      // API caller errors 4XX are tagged as low
      span.setAttribute("error.severity", detail.getStatus() >= 500 ? "HIGH" : "LOW");
      // Retrieve the error code and convert it to String if present
      Optional.ofNullable(detail.getProperties().get(ERROR_CODE))
          .map(Object::toString) // Convert to String only if not null
          .ifPresent(code -> span.setAttribute("error.code", code));

      detail.setProperty(TRACE_ID, span.getSpanContext().getTraceId());
      detail.setProperty(SPAN_ID, span.getSpanContext().getSpanId());
    }
    
     */
  }

  /** Adds optional request metadata that might not be present in all environments (e.g., cloud). */
  private void addOptionalMetadata(ProblemDetail detail, HttpServletRequest request) {
    Map<String, Supplier<String>> metadataSuppliers =
        Map.of(
            "forwardedFor",
            () -> request.getHeader("X-Forwarded-For"),
            "queryParams",
            request::getQueryString,
            "protocol",
            request::getProtocol,
            "scheme",
            request::getScheme,
            "referrer",
            () -> request.getHeader("Referer"),
            "acceptLanguage",
            () -> request.getHeader("Accept-Language"));

    // Iterate through the suppliers and set properties if values are non-null
    metadataSuppliers.forEach(
        (key, supplier) ->
            Optional.ofNullable(supplier.get()).ifPresent(value -> detail.setProperty(key, value)));

    // Handle the boolean separately
    detail.setProperty("isSecure", String.valueOf(request.isSecure()));
  }
}
