package org.observability.otel.rest;

import static org.observability.otel.rest.ApiConstants.ApiPath.BASE_V1_API_PATH;
import static org.observability.otel.rest.ApiConstants.ApiPath.CUSTOMERS;
import static org.observability.otel.rest.ApiConstants.ApiPath.ID_PATH_VAR;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.observability.otel.domain.Customer;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@Tag(
    name = "Customers",
    description = "Endpoints for managing customer data with OpenTelemetry tracing.")
@RequestMapping(
    value = BASE_V1_API_PATH,
    produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE}
    // consumes = MediaType.APPLICATION_JSON_VALUE
    )
public interface CustomerAPI {

  @Operation(summary = "Retrieve a customer by ID")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Customer found successfully"),
        @ApiResponse(responseCode = "404", description = "Customer not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
      })
  @GetMapping(value = CUSTOMERS + ID_PATH_VAR)
  ResponseEntity<Customer> getCustomer(@PathVariable @NotNull @Min(1) Long id);

  @Operation(summary = "Retrieve all customers")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Customers retrieved successfully"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
      })
  @GetMapping(value = CUSTOMERS)
  ResponseEntity<List<Customer>> getAllCustomers();

  @Operation(summary = "Create a new customer")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "201", description = "Customer created successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid customer data"),
        @ApiResponse(responseCode = "409", description = "Customer already exists"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
      })
  @PostMapping(value = CUSTOMERS,consumes = MediaType.APPLICATION_JSON_VALUE)
  ResponseEntity<Customer> createCustomer(@Valid @RequestBody Customer customer);

  @Operation(summary = "Update an existing customer")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Customer updated successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid customer data"),
        @ApiResponse(responseCode = "404", description = "Customer not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
      })
  @PutMapping(value = CUSTOMERS + ID_PATH_VAR, consumes = MediaType.APPLICATION_JSON_VALUE)
  ResponseEntity<Customer> updateCustomer(
      @PathVariable @NotNull @Min(1) Long id, @Valid @RequestBody Customer customer);

  @Operation(summary = "Delete a customer")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "204", description = "Customer deleted successfully"),
        @ApiResponse(responseCode = "404", description = "Customer not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
      })
  @DeleteMapping(value = CUSTOMERS + ID_PATH_VAR)
  ResponseEntity<Void> deleteCustomer(@PathVariable @NotNull @Min(1) Long id);
}
