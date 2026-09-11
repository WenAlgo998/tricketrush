ALTER TABLE outbox_events
    ADD COLUMN dead_lettered BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN dead_lettered_at TIMESTAMPTZ;

CREATE INDEX idx_outbox_events_retry_ready ON outbox_events (next_attempt_at, created_at)
    WHERE published = FALSE AND dead_lettered = FALSE;

CREATE TABLE audit_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type TEXT NOT NULL,
    aggregate_id UUID NOT NULL,
    action TEXT NOT NULL,
    details JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_log_aggregate_created_at
    ON audit_log (aggregate_type, aggregate_id, created_at DESC);
