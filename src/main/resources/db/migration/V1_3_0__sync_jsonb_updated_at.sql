-- V1_2_0 updated the updated_at column for rows that contained documents,
-- but did not update the updatedAt field inside the customer_json JSONB blob.
-- This migration syncs them: wherever the column is newer than the JSONB field,
-- overwrite the JSONB field with the column value.
--
-- Format: ISO-8601 UTC with microseconds — matches Jackson Instant serialisation
-- (e.g. "2026-02-26T19:09:10.831914Z").
--
-- The WHERE clause is idempotent; running this migration again is a no-op.

UPDATE customers
SET customer_json = jsonb_set(
    customer_json,
    '{updatedAt}',
    to_jsonb(to_char(updated_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"'))
)
WHERE (customer_json->>'updatedAt')::timestamptz < updated_at;
