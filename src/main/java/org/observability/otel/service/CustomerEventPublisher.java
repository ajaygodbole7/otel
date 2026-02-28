package org.observability.otel.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.cloudevents.CloudEvent;
import io.cloudevents.jackson.JsonCloudEventData;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.hypersistence.tsid.TSID;
import java.net.URI;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.observability.otel.domain.Customer;
import org.observability.otel.exception.CustomerServiceException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/*
 * Service to Publish Customer CloudEvents to Kafka
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CustomerEventPublisher {
  private static final String TOPIC = "customer-events";
  private final KafkaTemplate<String, CloudEvent> kafkaTemplate;
  private final ObjectMapper objectMapper;

  // Event Types
  private static final String EVENT_TYPE_CREATED = "Customer::created";
  private static final String EVENT_TYPE_UPDATED = "Customer::updated";
  private static final String EVENT_TYPE_DELETED = "Customer::deleted";


  public void publishCustomerCreated(Customer customer) {
    publishEvent(EVENT_TYPE_CREATED, customer);
  }

  public void publishCustomerUpdated(Customer customer) {
    publishEvent(EVENT_TYPE_UPDATED, customer);
  }

  public void publishCustomerDeleted(Customer customer) {
    publishEvent(EVENT_TYPE_DELETED, customer);
  }

  private void publishEvent(String eventType, Customer customer) {
    if (customer == null) {
      throw new IllegalArgumentException("Customer cannot be null");
    }
    if (customer.id() == null) {
      throw new IllegalArgumentException("Customer ID cannot be null");
    }

    try {
      var customerJsonNode = objectMapper.valueToTree(customer);
      var cloudEvent = CloudEventBuilder.v1()
          .withId(String.valueOf(TSID.Factory.getTsid().toLong()))
          .withSource(URI.create("/customer/events"))
          .withType(eventType)
          .withTime(OffsetDateTime.now(ZoneOffset.UTC))
          .withSubject(customer.id().toString())
          .withDataContentType("application/json")
          .withData(JsonCloudEventData.wrap(customerJsonNode))
          .build();

      var result = kafkaTemplate.send(TOPIC, cloudEvent.getId(), cloudEvent)
          .get(5, TimeUnit.SECONDS);
      log.info("Published {} event for customer {} to partition {} offset {}",
               eventType, customer.id(),
               result.getRecordMetadata().partition(),
               result.getRecordMetadata().offset());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new CustomerServiceException("Kafka send interrupted", e);
    } catch (Exception e) {
      log.error("Error publishing event for customer {}: {}",
                customer.id(), e.getMessage(), e);
      throw new CustomerServiceException("Failed to publish event", e);
    }
  }
}
