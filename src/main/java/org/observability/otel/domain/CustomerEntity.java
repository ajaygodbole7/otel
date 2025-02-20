package org.observability.otel.domain;

import io.hypersistence.tsid.TSID;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Type;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "customers")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerEntity extends AbstractAuditingEntity<Long>{

  @Id
  @JdbcTypeCode(SqlTypes.BIGINT)
  @Column(name = "customer_id", updatable = false, nullable = false)
  private Long customerId;

  @Type(JsonType.class)
  @Column(columnDefinition = "jsonb")
  private String customerJson;

  //private Instant createdAt;
  //private Instant updatedAt;

  @PrePersist
  void prePersist() {
    if (customerId == null) {
      customerId = TSID.Factory.getTsid().toLong();
    }
    setCreatedDate( = Instant.now();
    updatedAt = createdAt;
  }

  @PreUpdate
  void preUpdate() {
    updatedAt = Instant.now();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof CustomerEntity that)) return false;
    return Objects.equals(customerId, that.customerId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(customerId);
  }

  @Override
  public String toString() {
    return "CustomerEntity{"
        + "id='"
        + customerId
        + '\''
        + ", createdAt="
        + createdAt
        + ", updatedAt="
        + updatedAt
        + '}';
  }

  public void setCustomerJson(String customerJson) {
    this.customerJson = customerJson;
  }

  public void setCustomerId(Long id) {
    this.customerId = id;
  }

}
