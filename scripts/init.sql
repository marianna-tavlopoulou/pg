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
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    version         BIGINT         NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_merchant_status ON payment_orders (merchant_id, status);
CREATE INDEX IF NOT EXISTS idx_idempotency_key ON payment_orders (idempotency_key);


-- 4. Idempotent Data Seeding
INSERT INTO payment_orders (
    id, 
    merchant_id, 
    amount, 
    currency, 
    method, 
    status, 
    idempotency_key, 
    description, 
    version
)
VALUES (
    'a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d', 
    '00000000-0000-0000-0000-000000000001', 
    250.5000,                              
    'EUR', 
    'CARD', 
    'COMPLETED', 
    'idem-stub-key-0001',                  
    'Initial mock stub transaction for testing', 
    0                                      
)
ON CONFLICT (idempotency_key) DO NOTHING;