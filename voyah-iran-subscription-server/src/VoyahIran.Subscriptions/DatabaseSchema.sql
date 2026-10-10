CREATE TABLE IF NOT EXISTS subscriptions (
  vin TEXT PRIMARY KEY NOT NULL,
  created_at TIMESTAMPTZ NOT NULL,
  expires_at TIMESTAMPTZ NOT NULL,
  renewed_at TIMESTAMPTZ NOT NULL
);
-- Existing subscriptions become VIP; later restarts preserve explicit false values.
ALTER TABLE subscriptions ADD COLUMN IF NOT EXISTS vip_user BOOLEAN NOT NULL DEFAULT TRUE;
CREATE TABLE IF NOT EXISTS renewal_events (
  id BIGSERIAL PRIMARY KEY,
  vin TEXT NOT NULL,
  occurred_at TIMESTAMPTZ NOT NULL,
  previous_expiry TIMESTAMPTZ,
  new_expiry TIMESTAMPTZ NOT NULL,
  reason TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS renewal_events_vin ON renewal_events(vin);
CREATE TABLE IF NOT EXISTS subscription_blocks(vin TEXT PRIMARY KEY NOT NULL, blocked_at TIMESTAMPTZ NOT NULL, reason TEXT NOT NULL);
CREATE TABLE IF NOT EXISTS block_events(id BIGSERIAL PRIMARY KEY, vin TEXT NOT NULL, blocked INTEGER NOT NULL, occurred_at TIMESTAMPTZ NOT NULL, reason TEXT NOT NULL);

-- Legacy values are Unix seconds. Startup runs this atomically with schema creation.
DO $migration$
DECLARE
    target record;
    column_type oid;
BEGIN
    FOR target IN SELECT * FROM (VALUES
        ('subscriptions', 'created_at'), ('subscriptions', 'expires_at'),
        ('subscriptions', 'renewed_at'), ('renewal_events', 'occurred_at'),
        ('renewal_events', 'previous_expiry'), ('renewal_events', 'new_expiry'),
        ('subscription_blocks', 'blocked_at'), ('block_events', 'occurred_at')
    ) AS columns_to_migrate(table_name, column_name)
    LOOP
        SELECT a.atttypid INTO column_type FROM pg_attribute a
        WHERE a.attrelid = to_regclass(format('%I.%I', current_schema(), target.table_name))
          AND a.attname = target.column_name AND NOT a.attisdropped;
        IF column_type = 'bigint'::regtype THEN
            EXECUTE format(
                'ALTER TABLE %I.%I ALTER COLUMN %I TYPE timestamptz USING to_timestamp(%I::double precision)',
                current_schema(), target.table_name, target.column_name, target.column_name);
        ELSIF column_type IS DISTINCT FROM 'timestamptz'::regtype::oid THEN
            RAISE EXCEPTION 'Unexpected date column type for %.%: %',
                target.table_name, target.column_name, column_type;
        END IF;
    END LOOP;
END;
$migration$;
