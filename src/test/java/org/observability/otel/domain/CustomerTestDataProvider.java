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
        .emails(List.of(createPrimaryEmail()))
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
        .emails(List.of(createPrimaryEmail()))
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
            new Phone("MOBILE", "1", faker.phoneNumber().cellPhone()),
            new Phone("WORK", "1", faker.phoneNumber().phoneNumber()),
            new Phone("HOME", "1", faker.phoneNumber().phoneNumber())
        ))
        .documents(List.of(
            new Document("USA", "DRIVER_LICENSE", faker.bothify("??########")),
            new Document("USA", "PASSPORT", faker.numerify("#########")),
            new Document("USA", "SSN", faker.numerify("###-##-####"))
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
