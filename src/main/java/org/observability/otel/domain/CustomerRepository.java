package org.observability.otel.domain;



import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository for Customer entities.
 */
@Repository
public interface CustomerRepository extends JpaRepository<CustomerEntity, Long> {

  @Query(value = "SELECT * FROM customers " +
      "WHERE EXISTS (" +
      "  SELECT 1 FROM jsonb_array_elements(customer_json->'emails') AS email " +
      "  WHERE email->>'email' = :email" +
      ")", nativeQuery = true)
  Optional<CustomerEntity> findByEmail(@Param("email") String email);

  @Query(value = "SELECT * FROM customers " +
      "WHERE EXISTS (" +
      "  SELECT 1 FROM jsonb_array_elements(customer_json->'documents') AS doc " +
      "  WHERE doc->>'type' = 'SSN' AND doc->>'identifier' = :ssn" +
      ")", nativeQuery = true)
  Optional<CustomerEntity> findBySSN(@Param("ssn") String ssn);

  @Query("SELECT c FROM CustomerEntity c WHERE (:afterId IS NULL OR c.id > :afterId) ORDER BY c.id ASC")
  List<CustomerEntity> findNextPage(@Param("afterId") Long afterId, Limit limit);

}
