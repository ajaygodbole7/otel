-- Remove the documents field from all existing customer JSONB records.
-- Scrubs SSN, DRIVER_LICENSE, and PASSPORT identifiers from the column.
-- One-way: no DOWN migration. Intentional data removal.
UPDATE customers
SET customer_json = customer_json - 'documents',
    updated_at    = now()
WHERE customer_json ? 'documents';
