package org.observability.otel.exception;

/**
 * Custom exception thrown when a customer is not found.
 * Maps to HTTP 404 - Resource not found.
 */
public class CustomerNotFoundException extends RuntimeException {

  /**
   *
   * @param message The error message.
   */
  public CustomerNotFoundException(String message) {
    super(message);
  }

  /**
   *
   * @param message The error message.
   * @param cause   The underlying cause of the exception.
   */
  public CustomerNotFoundException(String message, Throwable cause) {
    super(message, cause);
  }
}

