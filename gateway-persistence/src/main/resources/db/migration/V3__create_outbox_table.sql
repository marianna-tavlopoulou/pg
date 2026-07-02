CREATE TABLE outbox_events (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_id UUID NOT NULL,        -- the payment_order id
    event_type  VARCHAR(50) NOT NULL,  -- PAYMENT_COMPLETED, PAYMENT_DECLINED, etc.
    payload     JSONB NOT NULL,        -- serialized PaymentOrder snapshot
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    published   BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX idx_outbox_unpublished ON outbox_events (published, created_at)
    WHERE published = FALSE;