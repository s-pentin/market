-- Заказ получает жизненный цикл (PENDING_PAYMENT -> PAID / PAYMENT_FAILED)
ALTER TABLE orders ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'PENDING_PAYMENT';
ALTER TABLE orders ADD CONSTRAINT chk_orders_status CHECK (status IN ('PENDING_PAYMENT', 'PAID', 'PAYMENT_FAILED'));
ALTER TABLE orders ADD COLUMN created_at TIMESTAMP NOT NULL DEFAULT now();
ALTER TABLE orders ADD COLUMN paid_at TIMESTAMP NULL;
ALTER TABLE orders ADD COLUMN payment_id BIGINT NULL;
ALTER TABLE orders ADD COLUMN idempotency_key UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE;

CREATE INDEX IF NOT EXISTS idx_order_items_order_id ON order_items (order_id);

CREATE TABLE IF NOT EXISTS payment (
    id              BIGSERIAL PRIMARY KEY,
    order_id        BIGINT         NOT NULL,
    user_id         BIGINT         NOT NULL,
    idempotency_key UUID           NOT NULL UNIQUE,
    amount          NUMERIC(10, 2) NOT NULL,
    status          VARCHAR(20)    NOT NULL,
    created_at      TIMESTAMP      NOT NULL DEFAULT now(),

    CONSTRAINT chk_payment_status CHECK (status IN ('SUCCEEDED', 'FAILED'))
);

CREATE INDEX IF NOT EXISTS idx_payment_user_id ON payment (user_id);