ALTER TABLE outbox_events ADD COLUMN parked_at TIMESTAMPTZ;

DROP INDEX idx_outbox_unpublished;

CREATE INDEX idx_outbox_unpublished ON outbox_events (created_at)
    WHERE published = FALSE AND dead_lettered = FALSE AND parked_at IS NULL;
