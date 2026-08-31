ALTER TABLE payments
    ADD CONSTRAINT uq_payments_order_id UNIQUE (order_id);
