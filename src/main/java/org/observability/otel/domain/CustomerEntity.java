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
public class CustomerEntity {

  @Id
  @JdbcTypeCode(SqlTypes.BIGINT)
  @Column(name = "id", updatable = false, nullable = false)
  private Long id;

  @Type(JsonType.class)
  @Column(columnDefinition = "jsonb")
  private String customerJson;

  private Instant createdAt;
  private Instant updatedAt;

  @PrePersist
  void prePersist() {
    if (id == null) {
      id = TSID.Factory.getTsid().toLong();
    }
    createdAt = Instant.now();
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
    return Objects.equals(id, that.id);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }

  @Override
  public String toString() {
    return "CustomerEntity{"
        + "id='"
        + id
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

  public void setId(Long id) {
    this.id = id;
  }
}
