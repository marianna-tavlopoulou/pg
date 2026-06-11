ALTER TABLE payment_orders
ADD COLUMN customer_id UUID;

UPDATE payment_orders
SET customer_id = gen_random_uuid()
WHERE customer_id IS NULL;

ALTER TABLE payment_orders
ALTER COLUMN customer_id SET NOT NULL;