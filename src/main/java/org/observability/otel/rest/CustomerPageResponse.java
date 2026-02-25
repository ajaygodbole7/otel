package org.observability.otel.rest;

import org.observability.otel.domain.Customer;
import java.util.List;

public record CustomerPageResponse(
    List<Customer> data,
    Long nextCursor,   // null when hasMore=false
    boolean hasMore,
    int limit
) {}
