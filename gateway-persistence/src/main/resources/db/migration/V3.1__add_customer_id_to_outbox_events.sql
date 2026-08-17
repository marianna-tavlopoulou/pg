ALTER TABLE outbox_events
ADD COLUMN customer_id UUID;

UPDATE outbox_events oe
SET customer_id = po.customer_id
FROM payment_orders po
WHERE oe.aggregate_id = po.id
  AND oe.customer_id IS NULL;

UPDATE outbox_events
SET customer_id = gen_random_uuid()
WHERE customer_id IS NULL;

ALTER TABLE outbox_events
ALTER COLUMN customer_id SET NOT NULL;