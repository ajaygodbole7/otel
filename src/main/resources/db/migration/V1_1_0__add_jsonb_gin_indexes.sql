-- Remove redundant index: PRIMARY KEY already indexes id
DROP INDEX IF EXISTS idx_customers_id;

-- GIN index with jsonb_path_ops operator class
-- Supports @>, @@, and jsonb_path_query operators
-- Covers findByEmail native query
-- More compact than default jsonb_ops GIN index
CREATE INDEX idx_customers_jsonb_gin
    ON customers
    USING GIN (customer_json jsonb_path_ops);
