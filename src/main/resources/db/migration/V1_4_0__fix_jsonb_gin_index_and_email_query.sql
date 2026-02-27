-- V1_1_0 created the GIN index with jsonb_path_ops, which supports only @>, @@, @?.
-- The findByEmail query previously used jsonb_array_elements(), which cannot use any GIN index.
-- This migration drops the old index and recreates it with the default jsonb_ops operator class.
-- The findByEmail query is also rewritten to use @> containment (see CustomerRepository).

DROP INDEX IF EXISTS idx_customers_jsonb_gin;

-- Default jsonb_ops GIN: supports ?, ?|, ?&, @>, @?, @@ and containment checks.
CREATE INDEX idx_customers_jsonb_gin
    ON customers
    USING GIN (customer_json);
