-- Create Customer Table
CREATE TABLE customers (
    customer_id BIGINT PRIMARY KEY,  -- For TSID storage
    customer_json JSONB NOT NULL,
    created_by VARCHAR2(50) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_updated_by VARCHAR2(50) NOT NULL,
    last_updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

-- Create index on id for better performance
-- Maybe redundant due to Primary Key
CREATE INDEX idx_customers_id ON customers USING BTREE (customer_id);

-- Create index on created_at for time-based queries
CREATE INDEX idx_customers_created_at ON customers USING BTREE (created_at);
