package org.observability.otel.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
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
    updatedCustomer = CustomerTestDataProvider.createUpdateCustomer(
        basicCustomer.id(), basicCustomer.createdAt());

    eventPublisher = new CustomerEventPublisher(kafkaTemplate, objectMapper);
  }

  // Helper method to create a mock SendResult
  private SendResult<String, CloudEvent> createMockSendResult(int partition, long offset) {
    RecordMetadata metadata = new RecordMetadata(
        new TopicPartition(TOPIC, partition),
        offset, 0, 0L, 0, 0
    );
    return new SendResult<>(null, metadata);
  }

  @Test
  @DisplayName("Should throw CustomerServiceException when KafkaTemplate.send returns null")
  void shouldThrowWhenKafkaSendReturnsNull() {
    when(kafkaTemplate.send(anyString(), anyString(), any(CloudEvent.class))).thenReturn(null);

    assertThatThrownBy(() -> eventPublisher.publishCustomerCreated(basicCustomer))
        .isInstanceOf(CustomerServiceException.class)
        .hasMessageContaining("Failed to publish event");

    verify(kafkaTemplate).send(anyString(), anyString(), any(CloudEvent.class));
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
        new RuntimeException("Kafka send failed"));
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
  @DisplayName("Should throw IllegalArgumentException when customer ID is null")
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
  @DisplayName("Should set all CloudEvent attributes correctly")
  void shouldVerifyAllCloudEventAttributes() {
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
    assertThat(capturedEvent.getData()).isNotNull();
    assertThat(capturedEvent.getTime())
        .isNotNull()
        .isCloseTo(OffsetDateTime.now(ZoneOffset.UTC), within(1, ChronoUnit.MINUTES));
  }

  @Test
  @DisplayName("Should handle Kafka send timeout gracefully")
  void shouldHandleKafkaSendTimeoutGracefully() {
    CompletableFuture<SendResult<String, CloudEvent>> timeoutFuture = new CompletableFuture<>();
    timeoutFuture.completeExceptionally(new TimeoutException("Kafka timeout"));

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
  @DisplayName("Should embed customer data fields correctly in CloudEvent payload")
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

    JsonNode eventData = ((JsonCloudEventData) eventCaptor.getValue().getData()).getNode();
    assertThat(eventData.get("id").asLong()).isEqualTo(basicCustomer.id());
    assertThat(eventData.get("firstName").asText()).isEqualTo(basicCustomer.firstName());
    assertThat(eventData.get("lastName").asText()).isEqualTo(basicCustomer.lastName());
    assertThat(eventData.get("type").asText()).isEqualTo(basicCustomer.type());
  }

  @Test
  @DisplayName("Should serialize full customer with all nested data correctly into CloudEvent")
  void shouldSerializeFullCustomerDataCorrectlyInEvent() throws JsonProcessingException {
    // Given
    SendResult<String, CloudEvent> sendResult = createMockSendResult(0, 1L);
    when(kafkaTemplate.send(anyString(), anyString(), any(CloudEvent.class)))
        .thenReturn(CompletableFuture.completedFuture(sendResult));

    // When
    eventPublisher.publishCustomerCreated(fullCustomer);

    // Then
    ArgumentCaptor<CloudEvent> eventCaptor = ArgumentCaptor.forClass(CloudEvent.class);
    verify(kafkaTemplate).send(eq(TOPIC), anyString(), eventCaptor.capture());

    Customer captured = objectMapper.treeToValue(
        ((JsonCloudEventData) eventCaptor.getValue().getData()).getNode(), Customer.class);
    assertThat(captured)
        .usingRecursiveComparison()
        .isEqualTo(fullCustomer);
  }
}
