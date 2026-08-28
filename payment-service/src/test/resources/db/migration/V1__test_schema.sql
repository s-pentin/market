-- Минимальная схема для тестов payment-service: только таблицы, которые он трогает.
-- В проде схемой управляет market-app (Flyway); здесь payment-service самодостаточен.
CREATE TABLE IF NOT EXISTS balance
(
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT         NOT NULL UNIQUE,
    amount      NUMERIC(10, 2) NOT NULL,
    currency    VARCHAR(3)     NOT NULL,
    CONSTRAINT chk_balance_amount_positive CHECK (amount >= 0)
);

CREATE TABLE IF NOT EXISTS payment
(
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
