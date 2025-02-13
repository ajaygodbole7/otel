package org.observability.otel.exception;

/**
 * Custom exception thrown when an unexpected error occurs in the customer service.
 * Maps to HTTP 500 - Internal Server Error.
 */
public class CustomerServiceException extends RuntimeException {

  /**
   * @param message The error message.
   */
  public CustomerServiceException(String message) {
    super(message);
  }

  /**
   * @param message The error message.
   * @param cause   The underlying cause of the exception.
   */
  public CustomerServiceException(String message, Throwable cause) {
    super(message, cause);
  }
}
