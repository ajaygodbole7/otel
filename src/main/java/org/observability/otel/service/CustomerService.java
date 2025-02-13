package org.observability.otel.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.hypersistence.tsid.TSID;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.observability.otel.domain.Customer;
import org.observability.otel.domain.CustomerEntity;
import org.observability.otel.domain.CustomerRepository;
import org.observability.otel.exception.CustomerConflictException;
import org.observability.otel.exception.CustomerNotFoundException;
import org.observability.otel.exception.CustomerServiceException;
import org.observability.otel.exception.ServiceUnavailableException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service layer for managing Customer entities.
 * This class provides transactional CRUD (Create, Read, Update, Delete) operations
 * and publishes events for customer changes.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Slf4j
public class CustomerService {

  private final CustomerRepository customerRepository;
  private final ObjectMapper objectMapper;
  private final CustomerEventPublisher eventPublisher;

  /**
   * Retrieve a customer by their ID.
   *
   * @param id The ID of the customer to retrieve
   * @return The Customer object if found
   * @throws CustomerNotFoundException if no customer is found with the given ID
   */
  public Customer findById(Long id) {
    log.info("Finding customer by ID: {}", id);
    try {
      Customer customer = customerRepository
          .findById(id)
          .map(this::convertToCustomer)
          .orElseThrow(() -> new CustomerNotFoundException("Customer with ID " + id + " not found."));
      log.info("Successfully found customer with ID: {}", id);
      log.debug("Retrieved customer details: {}", customer);
      return customer;
    } catch (Exception e) {
      log.error("Error finding customer with ID: {}", id, e);
      return translateAndThrow(e, "Error retrieving customer with ID: " + id);
    }
  }

  /**
   * Create a new customer in the database.
   * Generates a new ID, sets timestamps, and publishes a creation event.
   *
   * @param customer The customer to create
   * @return The created Customer object with generated ID and timestamps
   * @throws CustomerConflictException if a customer with the same ID already exists
   * @throws IllegalArgumentException if the customer data is invalid
   */
  @Transactional
  public Customer create(Customer customer) {
    log.info("Starting customer creation process");
    log.debug("Input customer data: {}", customer);
    validateNewCustomer(customer);

    try {
      Long customerId = TSID.Factory.getTsid().toLong();
      log.debug("Generated new customer ID: {}", customerId);
      Instant now = Instant.now();

      Customer customerToSave = Customer.builder()
          .id(customerId)
          .type(customer.type())
          .firstName(customer.firstName())
          .lastName(customer.lastName())
          .middleName(customer.middleName())
          .suffix(customer.suffix())
          .emails(customer.emails())
          .phones(customer.phones())
          .addresses(customer.addresses())
          .documents(customer.documents())
          .createdAt(now)
          .updatedAt(now)
          .build();
      log.debug("Prepared customer for saving: {}", customerToSave);

      CustomerEntity entity = new CustomerEntity();
      entity.setId(customerId);
      entity.setCustomerJson(objectMapper.writeValueAsString(customerToSave));

      log.info("Saving customer to database");
      var savedEntity = customerRepository.saveAndFlush(entity);
      var savedCustomer = convertToCustomer(savedEntity);
      log.info("Successfully saved customer with ID: {}", savedCustomer.id());

      log.info("Publishing customer created event");
      eventPublisher.publishCustomerCreated(savedCustomer);
      log.info("Successfully published customer created event");

      return savedCustomer;
    } catch (Exception e) {
      log.error("Failed to create customer", e);
      return translateAndThrow(e, "Error creating customer");
    }
  }

  /**
   * Update an existing customer in the database.
   * Preserves the original creation timestamp and updates the modified timestamp.
   *
   * @param id The ID of the customer to update
   * @param inboundCustomer The updated customer data
   * @return The updated Customer object
   * @throws CustomerNotFoundException if the customer does not exist
   * @throws IllegalArgumentException if the ID doesn't match or customer data is invalid
   */
  @Transactional
  public Customer update(Long id, Customer inboundCustomer) {
    log.info("Starting customer update process for ID: {}", id);
    log.debug("Update request data: {}", inboundCustomer);
    validateExistingCustomer(id, inboundCustomer);

    try {
      log.debug("Finding existing customer entity");
      CustomerEntity existingEntity = customerRepository.findById(id)
          .orElseThrow(() -> new CustomerNotFoundException("Customer not found"));
      log.debug("Found existing customer entity: {}", existingEntity);

      Customer customerToUpdate = Customer.builder()
          .id(existingEntity.getId())
          .type(inboundCustomer.type())
          .firstName(inboundCustomer.firstName())
          .lastName(inboundCustomer.lastName())
          .middleName(inboundCustomer.middleName())
          .suffix(inboundCustomer.suffix())
          .emails(inboundCustomer.emails())
          .phones(inboundCustomer.phones())
          .addresses(inboundCustomer.addresses())
          .documents(inboundCustomer.documents())
          .createdAt(existingEntity.getCreatedAt())
          .updatedAt(Instant.now())
          .build();
      log.debug("Prepared customer update: {}", customerToUpdate);

      existingEntity.setCustomerJson(objectMapper.writeValueAsString(customerToUpdate));
      log.info("Saving updated customer to database");
      var savedEntity = customerRepository.saveAndFlush(existingEntity);
      var updatedCustomer = convertToCustomer(savedEntity);
      log.info("Successfully updated customer with ID: {}", updatedCustomer.id());

      log.info("Publishing customer updated event");
      eventPublisher.publishCustomerUpdated(updatedCustomer);
      log.info("Successfully published customer updated event");

      return updatedCustomer;
    } catch (Exception e) {
      log.error("Failed to update customer with ID: {}", id, e);
      return translateAndThrow(e, "Error updating customer with ID: " + id);
    }
  }

  /**
   * Delete a customer by their ID.
   * Publishes a deletion event before removing the customer.
   *
   * @param id The ID of the customer to delete
   * @throws CustomerNotFoundException if the customer does not exist
   */
  @Transactional
  public void delete(Long id) {
    log.info("Starting customer deletion process for ID: {}", id);
    var customer = findById(id);
    try {
      log.info("Deleting customer from database");
      customerRepository.deleteById(id);
      log.info("Successfully deleted customer from database");

      log.info("Publishing customer deleted event");
      eventPublisher.publishCustomerDeleted(customer);
      log.info("Successfully published customer deleted event");
    } catch (Exception e) {
      log.error("Failed to delete customer with ID: {}", id, e);
      translateAndThrow(e, "Error deleting customer with ID: " + id);
    }
  }

  /**
   * Retrieve all customers from the database.
   *
   * @return List of all customers
   */
  public List<Customer> getAllCustomers() {
    log.info("Retrieving all customers");
    try {
      List<Customer> customers = customerRepository.findAll().stream()
          .map(this::convertToCustomer)
          .collect(Collectors.toList());
      log.info("Successfully retrieved {} customers", customers.size());
      log.debug("Retrieved customers: {}", customers);
      return customers;
    } catch (Exception e) {
      log.error("Failed to retrieve all customers", e);
      return translateAndThrow(e, "Error retrieving all customers");
    }
  }

  /**
   * Validate the input for creating a new customer.
   *
   * @param customer The customer to validate
   * @throws IllegalArgumentException if the customer is null
   * @throws CustomerConflictException if a customer with the same ID already exists
   */
  private void validateNewCustomer(Customer customer) {
    log.debug("Validating new customer: {}", customer);
    if (customer == null) {
      log.error("Customer validation failed: customer is null");
      throw new IllegalArgumentException("Customer must not be null.");
    }
    if (customer.id() != null && customerRepository.existsById(customer.id())) {
      log.error("Customer validation failed: customer with ID {} already exists", customer.id());
      throw new CustomerConflictException("Customer with ID " + customer.id() + " already exists.");
    }
    log.debug("Customer validation successful");
  }

  /**
   * Validate the input for updating an existing customer.
   *
   * @param id The ID of the customer to update
   * @param customer The customer data to validate
   * @throws IllegalArgumentException if the ID doesn't match or customer data is invalid
   * @throws CustomerNotFoundException if the customer does not exist
   */
  private void validateExistingCustomer(Long id, Customer customer) {
    log.debug("Validating existing customer - ID: {}, Customer: {}", id, customer);
    if (customer == null || customer.id() == null || !id.equals(customer.id())) {
      log.error("Customer validation failed: invalid customer data or ID mismatch");
      throw new IllegalArgumentException("Invalid customer data or ID mismatch.");
    }
    if (!customerRepository.existsById(id)) {
      log.error("Customer validation failed: customer with ID {} not found", id);
      throw new CustomerNotFoundException("Customer with ID " + id + " not found.");
    }
    log.debug("Customer validation successful");
  }

  /**
   * Convert a CustomerEntity to a Customer object.
   *
   * @param entity The entity to convert
   * @return The converted Customer object
   * @throws CustomerServiceException if JSON conversion fails
   */
  private Customer convertToCustomer(CustomerEntity entity) {
    try {
      log.debug("Converting entity to customer: {}", entity);
      Customer customer = objectMapper.readValue(entity.getCustomerJson(), Customer.class);
      log.debug("Successfully converted entity to customer: {}", customer);
      return customer;
    } catch (JsonProcessingException e) {
      log.error("Failed to convert entity to customer: {}", entity, e);
      throw new CustomerServiceException("Error converting JSON to customer", e);
    }
  }

  /**
   * Translates Spring Data JPA exceptions into application-specific exceptions.
   * This ensures that database-level exceptions are properly handled and
   * presented to the client with appropriate HTTP status codes via the ControllerAdvice.
   *
   * @param e The exception to translate
   * @param contextMessage A message providing context about the operation
   * @return Never returns - always throws an exception
   * @throws CustomerNotFoundException when the requested resource is not found
   * @throws CustomerConflictException when there's a data integrity violation
   * @throws ServiceUnavailableException when there are database timeout issues
   * @throws CustomerServiceException for other database access issues
   */
  private <T> T translateAndThrow(Exception e, String contextMessage) {
    log.error("Translating exception: {} with message: {}", e.getClass().getSimpleName(), contextMessage, e);

    RuntimeException translatedEx = switch (e) {
      case EmptyResultDataAccessException ex -> {
        log.debug("Resource not found: {}", contextMessage);
        yield new CustomerNotFoundException(contextMessage, ex);
      }
      case DataIntegrityViolationException ex -> {
        log.warn("Data integrity violation: {}", contextMessage);
        yield new CustomerConflictException(contextMessage + " Possible constraint violation.", ex);
      }
      case QueryTimeoutException ex -> {
        log.error("Database query timeout: {}", contextMessage);
        yield new ServiceUnavailableException(contextMessage + " due to query timing out.", ex);
      }
      case TransientDataAccessResourceException ex -> {
        log.error("Transient database error: {}", contextMessage);
        yield new ServiceUnavailableException(contextMessage + " due to a transient resource issue.", ex);
      }
      case DataAccessException ex -> {
        log.error("Database access error: {}", contextMessage);
        yield new CustomerServiceException(contextMessage + " due to data access error.", ex);
      }
      default -> {
        log.error("Unexpected error: {}", contextMessage);
        yield new CustomerServiceException(contextMessage + " due to unexpected error.", e);
      }
    };

    log.debug("Translated {} to {}", e.getClass().getSimpleName(), translatedEx.getClass().getSimpleName());
    throw translatedEx;
  }
}
