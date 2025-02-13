package org.observability.otel.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any; // Correct import for Mockito matchers
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.cloudevents.CloudEvent;
import io.cloudevents.jackson.JsonCloudEventData;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.observability.otel.domain.Customer;
import org.observability.otel.domain.CustomerTestDataProvider;
import org.observability.otel.exception.CustomerServiceException;
import org.observability.otel.service.CustomerEventPublisher;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

@ExtendWith(MockitoExtension.class)
class CustomerEventPublisherUnitTest {

  private static final String TOPIC = "customer-events";
  @Mock
  private KafkaTemplate<String, CloudEvent> kafkaTemplate;
  private ObjectMapper objectMapper;
  @InjectMocks
  private CustomerEventPublisher eventPublisher;
  // Test data
  private Customer basicCustomer;
  private Customer fullCustomer;
  private Customer updatedCustomer;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);
    basicCustomer = CustomerTestDataProvider.createBasicCustomer();
    fullCustomer = CustomerTestDataProvider.createFullCustomer();
    updatedCustomer =
        CustomerTestDataProvider.createUpdateCustomer(
            basicCustomer.id(), basicCustomer.createdAt());

    // Manually create the service with all dependencies
    eventPublisher = new CustomerEventPublisher(kafkaTemplate, objectMapper);
  }

  // Helper method to create a mock SendResult
  private SendResult<String, CloudEvent> createMockSendResult(int partition, long offset) {
    RecordMetadata metadata = new RecordMetadata(
        new TopicPartition(TOPIC, partition),
        offset,
        0,
        0L,
        0,
        0
    );
    return new SendResult<>(null, metadata);
  }
  @Test
  @DisplayName("Should publish customer created event successfully")
  void shouldPublishCustomerCreatedEvent() {
    lenient().when(kafkaTemplate.send(anyString(), anyString(), any(CloudEvent.class)))
        .thenReturn(CompletableFuture.completedFuture(null));


    eventPublisher.publishCustomerCreated(basicCustomer);

    verify(kafkaTemplate, times(1))
        .send(anyString(), anyString(), any(CloudEvent.class));
  }

  @Test
  @DisplayName("Should handle null KafkaTemplate response gracefully")
  void shouldHandleNullKafkaResponseGracefully() {
    when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(null);

    assertThatThrownBy(() -> eventPublisher.publishCustomerCreated(basicCustomer))
        .isInstanceOf(CustomerServiceException.class)
        .hasMessageContaining("Failed to publish event");

    verify(kafkaTemplate, times(1)).send(anyString(), anyString(), any());
  }

  @Test
  @DisplayName("Should publish customer created event successfully")
  void shouldPublishCustomerCreatedEventSuccessfully() {
    // Given
    SendResult<String, CloudEvent> sendResult = createMockSendResult(0, 1L);
    when(kafkaTemplate.send(anyString(), anyString(), any(CloudEvent.class)))
        .thenReturn(CompletableFuture.completedFuture(sendResult));

    // When
    eventPublisher.publishCustomerCreated(basicCustomer);

    // Then
    ArgumentCaptor<CloudEvent> eventCaptor = ArgumentCaptor.forClass(CloudEvent.class);
    verify(kafkaTemplate).send(eq(TOPIC), anyString(), eventCaptor.capture());

    CloudEvent capturedEvent = eventCaptor.getValue();
    assertThat(capturedEvent.getType()).isEqualTo("Customer::created");
    assertThat(capturedEvent.getSubject()).isEqualTo(basicCustomer.id().toString());
    assertThat(capturedEvent.getDataContentType()).isEqualTo("application/json");
  }

  @Test
  @DisplayName("Should publish customer updated event successfully")
  void shouldPublishCustomerUpdatedEventSuccessfully() {
    // Given
    SendResult<String, CloudEvent> sendResult = createMockSendResult(0, 1L);
    when(kafkaTemplate.send(anyString(), anyString(), any(CloudEvent.class)))
        .thenReturn(CompletableFuture.completedFuture(sendResult));

    // When
    eventPublisher.publishCustomerUpdated(updatedCustomer);

    // Then
    ArgumentCaptor<CloudEvent> eventCaptor = ArgumentCaptor.forClass(CloudEvent.class);
    verify(kafkaTemplate).send(eq(TOPIC), anyString(), eventCaptor.capture());

    CloudEvent capturedEvent = eventCaptor.getValue();
    assertThat(capturedEvent.getType()).isEqualTo("Customer::updated");
    assertThat(capturedEvent.getSubject()).isEqualTo(updatedCustomer.id().toString());
  }

  @Test
  @DisplayName("Should publish customer deleted event successfully")
  void shouldPublishCustomerDeletedEventSuccessfully() {
    // Given
    SendResult<String, CloudEvent> sendResult = createMockSendResult(0, 1L);
    when(kafkaTemplate.send(anyString(), anyString(), any(CloudEvent.class)))
        .thenReturn(CompletableFuture.completedFuture(sendResult));

    // When
    eventPublisher.publishCustomerDeleted(basicCustomer);

    // Then
    ArgumentCaptor<CloudEvent> eventCaptor = ArgumentCaptor.forClass(CloudEvent.class);
    verify(kafkaTemplate).send(eq(TOPIC), anyString(), eventCaptor.capture());

    CloudEvent capturedEvent = eventCaptor.getValue();
    assertThat(capturedEvent.getType()).isEqualTo("Customer::deleted");
    assertThat(capturedEvent.getSubject()).isEqualTo(basicCustomer.id().toString());
  }

  @Test
  @DisplayName("Should handle Kafka send failure")
  void shouldHandleKafkaSendFailure() {
    // Given
    var failedFuture = CompletableFuture.<SendResult<String, CloudEvent>>failedFuture(
        new RuntimeException("Kafka send failed")
                                                                                     );
    when(kafkaTemplate.send(anyString(), anyString(), any(CloudEvent.class)))
        .thenReturn(failedFuture);

    // When & Then
    assertThatThrownBy(() -> {
      eventPublisher.publishCustomerCreated(basicCustomer);
      failedFuture.join(); // Force completion of the CompletableFuture
    })
        .isInstanceOf(CompletionException.class)
        .hasCauseInstanceOf(RuntimeException.class)
        .hasRootCauseMessage("Kafka send failed");

    // Verify Kafka interaction
    verify(kafkaTemplate).send(eq(TOPIC), anyString(), any(CloudEvent.class));
  }

  @Test
  @DisplayName("Should throw CustomerServiceException when JSON serialization fails")
  void shouldThrowExceptionWhenSerializationFails() {
    // Given
    ObjectMapper mockMapper = mock(ObjectMapper.class);
    CustomerEventPublisher publisherWithMockMapper = new CustomerEventPublisher(kafkaTemplate, mockMapper);

    when(mockMapper.valueToTree(any()))
        .thenThrow(new IllegalArgumentException("Serialization failed"));

    // When/Then
    assertThatThrownBy(() -> publisherWithMockMapper.publishCustomerCreated(basicCustomer))
        .isInstanceOf(CustomerServiceException.class)
        .hasMessageContaining("Failed to publish event");
  }

  @Test
  @DisplayName("Should throw IllegalArgumentException when customer is null")
  void shouldThrowExceptionWhenCustomerIsNull() {
    assertThatThrownBy(() -> eventPublisher.publishCustomerCreated(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Customer cannot be null");

    verifyNoInteractions(kafkaTemplate);
  }

  @Test
  @DisplayName("Should handle null customer ID gracefully")
  void shouldHandleNullCustomerId() {
    // Given
    Customer customerWithNullId = Customer.builder()
        .id(null)
        .type("INDIVIDUAL")
        .firstName("John")
        .lastName("Doe")
        .build();

    // When/Then
    assertThatThrownBy(() -> eventPublisher.publishCustomerCreated(customerWithNullId))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Customer ID cannot be null");

    verifyNoInteractions(kafkaTemplate);
  }

  @Test
  @DisplayName("Should verify CloudEvent structure")
  void shouldVerifyCloudEventStructure() {
    // Given
    SendResult<String, CloudEvent> sendResult = createMockSendResult(0, 1L);
    when(kafkaTemplate.send(anyString(), anyString(), any(CloudEvent.class)))
        .thenReturn(CompletableFuture.completedFuture(sendResult));

    // When
    eventPublisher.publishCustomerCreated(basicCustomer);

    // Then
    ArgumentCaptor<CloudEvent> eventCaptor = ArgumentCaptor.forClass(CloudEvent.class);
    verify(kafkaTemplate).send(eq(TOPIC), anyString(), eventCaptor.capture());

    CloudEvent capturedEvent = eventCaptor.getValue();
    assertThat(capturedEvent.getId()).isNotNull();
    assertThat(capturedEvent.getSource().toString()).isEqualTo("/customer/events");
    assertThat(capturedEvent.getType()).isEqualTo("Customer::created");
    assertThat(capturedEvent.getTime()).isNotNull();
    assertThat(capturedEvent.getSubject()).isEqualTo(basicCustomer.id().toString());
    assertThat(capturedEvent.getDataContentType()).isEqualTo("application/json");
    assertThat(capturedEvent.getData()).isNotNull();
  }

  @Test
  @DisplayName("Should handle Kafka template returning null CompletableFuture")
  void shouldHandleNullCompletableFuture() {
    // Given
    when(kafkaTemplate.send(anyString(), anyString(), any(CloudEvent.class)))
        .thenReturn(null);

    // When/Then
    assertThatThrownBy(() -> eventPublisher.publishCustomerCreated(basicCustomer))
        .isInstanceOf(CustomerServiceException.class)
        .hasMessageContaining("Failed to publish event");
  }

  @Test
  @DisplayName("Should verify Kafka metadata in SendResult")
  void shouldVerifyKafkaMetadata() {
    // Given
    SendResult<String, CloudEvent> sendResult = createMockSendResult(1, 42L);
    when(kafkaTemplate.send(anyString(), anyString(), any(CloudEvent.class)))
        .thenReturn(CompletableFuture.completedFuture(sendResult));

    // When
    eventPublisher.publishCustomerCreated(basicCustomer);

    // Then
    verify(kafkaTemplate).send(eq(TOPIC), anyString(), any(CloudEvent.class));
    assertThat(sendResult.getRecordMetadata().partition()).isEqualTo(1);
    assertThat(sendResult.getRecordMetadata().offset()).isEqualTo(42L);
  }
  @Test
  @DisplayName("Should handle Kafka send timeout gracefully")
  void shouldHandleKafkaSendTimeoutGracefully() {
    CompletableFuture<SendResult<String, CloudEvent>> timeoutFuture = new CompletableFuture<>();
    timeoutFuture.completeExceptionally(new java.util.concurrent.TimeoutException("Kafka timeout"));

    when(kafkaTemplate.send(anyString(), anyString(), any(CloudEvent.class)))
        .thenReturn(timeoutFuture);

    assertThatThrownBy(() -> {
      eventPublisher.publishCustomerCreated(basicCustomer);
      timeoutFuture.join(); // Force completion of the CompletableFuture
    })
        .isInstanceOf(CompletionException.class)
        .hasCauseInstanceOf(TimeoutException.class)
        .hasRootCauseMessage("Kafka timeout");

    verify(kafkaTemplate).send(eq(TOPIC), anyString(), any(CloudEvent.class));
  }

  @Test
  @DisplayName("Should match CloudEvent data with original customer")
  void shouldMatchCloudEventDataWithOriginalCustomer() {
    // Given
    SendResult<String, CloudEvent> sendResult = createMockSendResult(0, 1L);
    when(kafkaTemplate.send(anyString(), anyString(), any(CloudEvent.class)))
        .thenReturn(CompletableFuture.completedFuture(sendResult));

    // When
    eventPublisher.publishCustomerCreated(basicCustomer);

    // Then
    ArgumentCaptor<CloudEvent> eventCaptor = ArgumentCaptor.forClass(CloudEvent.class);
    verify(kafkaTemplate).send(eq(TOPIC), anyString(), eventCaptor.capture());

    CloudEvent capturedEvent = eventCaptor.getValue();
    JsonNode eventData = ((JsonCloudEventData) capturedEvent.getData()).getNode();

    assertThat(eventData.get("id").asLong()).isEqualTo(basicCustomer.id());
    assertThat(eventData.get("firstName").asText()).isEqualTo(basicCustomer.firstName());
    assertThat(eventData.get("lastName").asText()).isEqualTo(basicCustomer.lastName());
    assertThat(eventData.get("type").asText()).isEqualTo(basicCustomer.type());
  }

  @Test
  @DisplayName("Should handle different customer data sizes")
  void shouldHandleDifferentCustomerDataSizes() throws JsonProcessingException {
    // Given
    SendResult<String, CloudEvent> sendResult = createMockSendResult(0, 1L);
    when(kafkaTemplate.send(anyString(), anyString(), any(CloudEvent.class)))
        .thenReturn(CompletableFuture.completedFuture(sendResult));

    // When & Then - Test with basic customer
    eventPublisher.publishCustomerCreated(basicCustomer);
    ArgumentCaptor<CloudEvent> basicEventCaptor = ArgumentCaptor.forClass(CloudEvent.class);
    verify(kafkaTemplate).send(eq(TOPIC), anyString(), basicEventCaptor.capture());
    CloudEvent basicEvent = basicEventCaptor.getValue();

    // Reset mock for full customer test
    reset(kafkaTemplate);
    when(kafkaTemplate.send(anyString(), anyString(), any(CloudEvent.class)))
        .thenReturn(CompletableFuture.completedFuture(sendResult));

    // Test with full customer
    eventPublisher.publishCustomerCreated(fullCustomer);
    ArgumentCaptor<CloudEvent> fullEventCaptor = ArgumentCaptor.forClass(CloudEvent.class);
    verify(kafkaTemplate).send(eq(TOPIC), anyString(), fullEventCaptor.capture());
    CloudEvent fullEvent = fullEventCaptor.getValue();

    // Convert CloudEvent data back to Customer objects
    Customer capturedBasicCustomer = objectMapper.treeToValue(
        ((JsonCloudEventData) basicEvent.getData()).getNode(), Customer.class);
    Customer capturedFullCustomer = objectMapper.treeToValue(
        ((JsonCloudEventData) fullEvent.getData()).getNode(), Customer.class);

    // Verify both events contain the correct customer data
    assertThat(capturedBasicCustomer)
        .usingRecursiveComparison()
        .isEqualTo(basicCustomer);

    assertThat(capturedFullCustomer)
        .usingRecursiveComparison()
        .isEqualTo(fullCustomer);
  }
  @Test
  @DisplayName("Should set all CloudEvent attributes correctly")
  void shouldSetAllCloudEventAttributesCorrectly() {
    // Given
    SendResult<String, CloudEvent> sendResult = createMockSendResult(0, 1L);
    when(kafkaTemplate.send(anyString(), anyString(), any(CloudEvent.class)))
        .thenReturn(CompletableFuture.completedFuture(sendResult));

    // When
    eventPublisher.publishCustomerCreated(basicCustomer);

    // Then
    ArgumentCaptor<CloudEvent> eventCaptor = ArgumentCaptor.forClass(CloudEvent.class);
    verify(kafkaTemplate).send(eq(TOPIC), anyString(), eventCaptor.capture());

    CloudEvent capturedEvent = eventCaptor.getValue();
    assertThat(capturedEvent.getSpecVersion().toString()).isEqualTo("1.0");
    assertThat(capturedEvent.getId()).isNotNull();
    assertThat(capturedEvent.getSource().toString()).isEqualTo("/customer/events");
    assertThat(capturedEvent.getType()).isEqualTo("Customer::created");
    assertThat(capturedEvent.getSubject()).isEqualTo(basicCustomer.id().toString());
    assertThat(capturedEvent.getDataContentType()).isEqualTo("application/json");
    assertThat(capturedEvent.getTime()).isNotNull();

    // Verify time is within the last minute
    assertThat(capturedEvent.getTime())
        .isCloseTo(OffsetDateTime.now(ZoneOffset.UTC), within(1, ChronoUnit.MINUTES));
  }

}
