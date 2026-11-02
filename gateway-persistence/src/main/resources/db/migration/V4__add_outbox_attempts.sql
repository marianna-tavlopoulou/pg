ALTER TABLE outbox_events
    ADD COLUMN attempts INT NOT NULL DEFAULT 0,
    ADD COLUMN last_error TEXT,
    ADD COLUMN dead_lettered BOOLEAN NOT NULL DEFAULT FALSE;

DROP INDEX idx_outbox_unpublished;

CREATE INDEX idx_outbox_unpublished ON outbox_events (published, created_at)
    WHERE published = FALSE AND dead_lettered = FALSE;