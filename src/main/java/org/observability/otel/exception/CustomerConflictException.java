package org.observability.otel.exception;

/**
 * Custom exception thrown when a conflict occurs, such as a data integrity violation.
 * Maps to HTTP 409 - Conflict.
 */
public class CustomerConflictException extends RuntimeException {

  /**
   * @param message The error message.
   */
  public CustomerConflictException(String message) {
    super(message);
  }

  /**
   * @param message The error message.
   * @param cause   The underlying cause of the exception.
   */
  public CustomerConflictException(String message, Throwable cause) {
    super(message, cause);
  }
}
