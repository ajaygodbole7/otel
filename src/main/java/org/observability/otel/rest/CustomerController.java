package org.observability.otel.rest;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.observability.otel.domain.Customer;
import org.observability.otel.service.CustomerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * REST controller implementation for Customer management.
 * This controller includes OpenTelemetry integration for distributed tracing.

 */
@RestController
@Slf4j
@RequiredArgsConstructor
public class CustomerController implements CustomerAPI {

  private final CustomerService customerService;

  /**
   * Retrieves a customer by their ID.
   * OpenTelemetry will automatically create a span for this operation,
   * including the HTTP method, path, and status code.
   */
  @Override
  public ResponseEntity<Customer> getCustomer( Long id) {
    log.debug("REST request to get Customer: {}", id);
    return ResponseEntity.ok(customerService.findById(id));
  }

  @Override
  public ResponseEntity<CustomerPageResponse> getCustomers(int limit, Long after) {
    log.info("Getting customers page: limit={}, after={}", limit, after);
    CustomerPageResponse page = customerService.getCustomers(after, limit);
    return ResponseEntity.ok(page);
  }


  /**
   * Creates a new customer.
   * The span for this operation will include the customer creation event
   * and any subsequent Kafka message publishing.
   */
  @Override
  public ResponseEntity<Customer> createCustomer(Customer customer) {
    log.debug("REST request to create Customer: {}", customer);
    var result = customerService.create(customer);
    return ResponseEntity
        .created(URI.create("/api/v1/customers/" + result.id()))
        .body(result);
  }

  /**
   * Updates an existing customer.
   * The span will track both the database update and event publishing,
   * allowing us to monitor the full update process.
   */
  @Override
  public ResponseEntity<Customer> updateCustomer(Long id, Customer customer) {
    log.debug("REST request to update Customer: {}", customer);
    return ResponseEntity.ok(customerService.update(id, customer));
  }

  /**
   * Deletes a customer by their ID.
   * The span will include both the database deletion and the
   * subsequent deletion event publishing.
   */
  @Override
  public ResponseEntity<Void> deleteCustomer( Long id) {
    log.debug("REST request to delete Customer: {}", id);
    customerService.delete(id);
    return ResponseEntity.noContent().build();
  }
}
