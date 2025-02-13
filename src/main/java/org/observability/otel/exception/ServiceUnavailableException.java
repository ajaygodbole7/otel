package org.observability.otel.exception;

/**
 * Custom exception thrown when the service is temporarily unavailable.
 * Maps to HTTP 503 - Service Unavailable.
 */
public class ServiceUnavailableException extends RuntimeException {

  /**
  * @param message The error message.
   */
  public ServiceUnavailableException(String message) {
    super(message);
  }

  /**
   * @param message The error message.
   * @param cause   The underlying cause of the exception.
   */
  public ServiceUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}
