package org.observability.otel.domain;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import java.time.Instant;
import java.util.List;

@Builder
public record Customer(
    //@NotNull(message = "ID cannot be null")
    Long id,
    String type,
    String firstName,
    String middleName,
    String lastName,
    String suffix,
    List<Address> addresses,
    List<Email> emails,
    List<Phone> phones,
    List<Document> documents,
    Instant createdAt,
    Instant updatedAt
) {}

record Address(
    String type,
    String line1,
    String line2,
    String line3,
    String city,
    String state,
    String postalCode,
    String country
) {}

record Email(
    boolean primary,
    String email,
    String type
) {}

record Phone(
    String type,
    String countryCode,
    String number
) {}

record Document(
    String country,
    String type,
    String identifier
) {}
