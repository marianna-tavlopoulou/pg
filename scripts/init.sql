CREATE TABLE IF NOT EXISTS payment_orders (
    id              UUID PRIMARY KEY,
    merchant_id     UUID           NOT NULL,
    amount          NUMERIC(19, 4) NOT NULL,
    currency        VARCHAR(3)        NOT NULL,
    method          VARCHAR(20)    NOT NULL,
    status          VARCHAR(20)    NOT NULL,
    idempotency_key VARCHAR(255)   NOT NULL UNIQUE,
    description     VARCHAR(500),
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_merchant_status ON payment_orders (merchant_id, status);
CREATE INDEX IF NOT EXISTS idx_idempotency_key ON payment_orders (idempotency_key);
