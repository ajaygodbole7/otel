package org.observability.otel.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.hypersistence.tsid.TSID;
import net.datafaker.Faker;

import java.time.Instant;
import java.util.List;

public class CustomerTestDataProvider {
  private static final Faker faker = new Faker();

  private static final ObjectMapper objectMapper = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);

  public static Customer createBasicCustomer() {
    Instant now = Instant.now();

    return Customer.builder()
        .id(TSID.Factory.getTsid().toLong())
        .type("INDIVIDUAL")
        .firstName(faker.name().firstName())
        .lastName(faker.name().lastName())
        .addresses(List.of(createAddress("HOME")))
        .emails(List.of(createPrimaryEmail()))
        .phones(List.of(new Phone("MOBILE", "1", "555-123-4567")))
        .createdAt(now)
        .updatedAt(now)
        .build();
  }

  public static Customer createUpdateCustomer(Long savedId, Instant createdAt) {

    return Customer.builder()
        .id(savedId)
        .type("INDIVIDUAL")
        .firstName(faker.name().firstName())
        .lastName(faker.name().lastName())
        .addresses(List.of(createAddress("HOME")))
        .emails(List.of(createPrimaryEmail()))
        .phones(List.of(new Phone("MOBILE", "1", "555-123-4567")))
        .createdAt(createdAt)
        .updatedAt(Instant.now().plusMillis(100))
        .build();
  }

  public static Customer createFullCustomer() {
    Instant now = Instant.now();

    return Customer.builder()
        .id(TSID.Factory.getTsid().toLong())
        .type("INDIVIDUAL")
        .firstName(faker.name().firstName())
        .middleName(faker.name().firstName())
        .lastName(faker.name().lastName())
        .suffix(faker.name().suffix())
        .addresses(List.of(
            createAddress("HOME"),
            createAddress("WORK")
        ))
        .emails(List.of(
            createPrimaryEmail(),
            createEmail(false, "WORK")
        ))
        .phones(List.of(
            new Phone("MOBILE", "1", "555-123-4567"),
            new Phone("WORK", "1", "555-987-6543"),
            new Phone("HOME", "1", "555-111-2222")
        ))
        .createdAt(now)
        .updatedAt(now)
        .build();
  }

  private static Address createAddress(String type) {
    var address = faker.address();
    return new Address(
        type,
        address.streetAddress(),
        address.secondaryAddress(),
        faker.number().numberBetween(1, 20) + " Floor",
        address.city(),
        address.stateAbbr(),
        address.zipCode(),
        "USA"
    );
  }

  private static Email createPrimaryEmail() {
    return createEmail(true, "PERSONAL");
  }

  private static Email createEmail(boolean primary, String type) {
    return new Email(primary, faker.internet().emailAddress(), type);
  }

  /** Creates a customer with firstName set to null (missing). */
  public static Customer createCustomerWithMissingFirstName(Customer base) {
    return Customer.builder()
        .type(base.type())
        .lastName(base.lastName())
        .addresses(base.addresses())
        .emails(base.emails())
        .phones(base.phones())
        .build();
  }

  /** Creates a customer with a blank lastName (whitespace only). */
  public static Customer createCustomerWithBlankLastName(Customer base) {
    return Customer.builder()
        .type(base.type())
        .firstName(base.firstName())
        .lastName("   ")
        .addresses(base.addresses())
        .emails(base.emails())
        .phones(base.phones())
        .build();
  }

  /** Creates a customer with an empty type string. */
  public static Customer createCustomerWithBlankType(Customer base) {
    return Customer.builder()
        .type("")
        .firstName(base.firstName())
        .lastName(base.lastName())
        .addresses(base.addresses())
        .emails(base.emails())
        .phones(base.phones())
        .build();
  }

  /** Creates a customer with an empty emails list. */
  public static Customer createCustomerWithEmptyEmails(Customer base) {
    return Customer.builder()
        .type(base.type())
        .firstName(base.firstName())
        .lastName(base.lastName())
        .addresses(base.addresses())
        .emails(java.util.Collections.emptyList())
        .phones(base.phones())
        .build();
  }

  /** Creates a customer whose first email has an invalid email address (not-an-email format). */
  public static Customer createCustomerWithInvalidEmailFormat(Customer base) {
    return Customer.builder()
        .type(base.type())
        .firstName(base.firstName())
        .lastName(base.lastName())
        .addresses(base.addresses())
        .emails(List.of(new Email(true, "notanemail", "PERSONAL")))
        .phones(base.phones())
        .build();
  }

  /** Creates a customer whose first phone has a number that fails the pattern constraint. */
  public static Customer createCustomerWithInvalidPhoneNumber(Customer base) {
    return Customer.builder()
        .type(base.type())
        .firstName(base.firstName())
        .lastName(base.lastName())
        .addresses(base.addresses())
        .emails(base.emails())
        .phones(List.of(new Phone("MOBILE", "1", "abc")))
        .build();
  }

  public static CustomerEntity createBasicCustomerEntity() {
    Customer basicCustomer = createBasicCustomer();
    return createCustomerEntity(basicCustomer);
  }

  public static CustomerEntity createFullCustomerEntity() {
    Customer fullCustomer = createFullCustomer();
    return createCustomerEntity(fullCustomer);
  }

  public static CustomerEntity createCustomerEntity(Customer customer) {
    try {
      return CustomerEntity.builder()
          .id(customer.id())
          .customerJson(objectMapper.writeValueAsString(customer))
          .createdAt(customer.createdAt())
          .updatedAt(customer.updatedAt())
          .build();
    } catch (JsonProcessingException e) {
      throw new RuntimeException("Error serializing customer to JSON", e);
    }
  }
}
