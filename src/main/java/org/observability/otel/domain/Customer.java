package org.observability.otel.domain;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import java.time.Instant;
import java.util.List;

@Builder
public record Customer(
    Long id,
    @NotBlank @Size(max = 50) String type,
    @NotBlank @Size(min = 1, max = 100) String firstName,
    @NotBlank @Size(min = 1, max = 100) String lastName,
    @Size(max = 100) String middleName,
    @Size(max = 50) String suffix,
    @Valid @NotEmpty List<Address> addresses,
    @Valid @NotEmpty List<Email> emails,
    @Valid @NotEmpty List<Phone> phones,
    Instant createdAt,
    Instant updatedAt
) {}

record Address(
    @NotBlank @Size(max = 50) String type,
    @NotBlank @Size(max = 255) String line1,
    @Size(max = 255) String line2,
    @Size(max = 255) String line3,
    @NotBlank @Size(max = 100) String city,
    @NotBlank @Size(max = 100) String state,
    @NotBlank @Size(max = 20) String postalCode,
    @NotBlank @Size(max = 100) String country
) {}

record Email(
    boolean primary,
    @NotBlank @jakarta.validation.constraints.Email String email,
    @NotBlank @Size(max = 50) String type
) {}

record Phone(
    @NotBlank @Size(max = 50) String type,
    @NotBlank @Size(max = 5) String countryCode,
    @NotBlank @Pattern(regexp = "^[0-9\\-\\+\\(\\) ]{7,20}$") String number
) {}
