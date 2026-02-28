-- Enforce email uniqueness at the database level.
--
-- The application previously relied on a check-then-act pattern in the service
-- layer (checkEmailUniqueness), which had a TOCTOU race: two concurrent
-- transactions could both pass the check and insert the same email.
--
-- This trigger runs BEFORE INSERT/UPDATE inside the same transaction.
-- pg_advisory_xact_lock serialises concurrent transactions that touch the same
-- email address, closing the race window.  The advisory lock is released
-- automatically on COMMIT/ROLLBACK.
--
-- The containment query  customer_json @> ...  uses the existing GIN index
-- created in V1_4_0.

CREATE OR REPLACE FUNCTION check_email_uniqueness()
RETURNS TRIGGER AS $$
DECLARE
  email_rec  RECORD;
  conflict_id BIGINT;
BEGIN
  -- Nothing to check if the JSONB has no emails array
  IF NEW.customer_json -> 'emails' IS NULL THEN
    RETURN NEW;
  END IF;

  FOR email_rec IN
    SELECT elem ->> 'email' AS addr
    FROM jsonb_array_elements(NEW.customer_json -> 'emails') AS elem
    WHERE elem ->> 'email' IS NOT NULL
  LOOP
    -- Serialise on a hash of the email address (transaction-scoped lock)
    PERFORM pg_advisory_xact_lock(hashtext(email_rec.addr));

    -- Check whether any OTHER customer already owns this email
    SELECT id INTO conflict_id
    FROM customers
    WHERE id != NEW.id
      AND customer_json @> jsonb_build_object(
            'emails',
            jsonb_build_array(jsonb_build_object('email', email_rec.addr))
          );

    IF FOUND THEN
      RAISE EXCEPTION 'Email % is already in use by customer %',
            email_rec.addr, conflict_id
        USING ERRCODE = '23505';  -- unique_violation
    END IF;
  END LOOP;

  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_check_email_uniqueness
  BEFORE INSERT OR UPDATE ON customers
  FOR EACH ROW
  EXECUTE FUNCTION check_email_uniqueness();
