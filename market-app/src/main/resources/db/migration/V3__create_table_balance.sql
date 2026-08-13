CREATE TABLE IF NOT EXISTS balance
(
    id          BIGSERIAL PRIMARY KEY,
    amount      NUMERIC(10, 2) NOT NULL DEFAULT 5000.00,
    currency    VARCHAR(3)     NOT NULL DEFAULT 'RUB',

    CONSTRAINT chk_balance_amount_positive CHECK (amount >= 0)
);

INSERT INTO balance (id, amount, currency)
VALUES (1, 5000.00, 'RUB')
ON CONFLICT DO NOTHING;