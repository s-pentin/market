CREATE TABLE IF NOT EXISTS product
(
    id          BIGSERIAL PRIMARY KEY,
    title       VARCHAR(200)    NOT NULL,
    description TEXT,
    img_path    VARCHAR(500),
    price       NUMERIC(10, 2)  NOT NULL,

    CONSTRAINT chk_product_price_positive CHECK (price > 0),
    CONSTRAINT chk_product_title_min_length CHECK (length(trim(title)) >= 3)
);

CREATE TABLE IF NOT EXISTS orders
(
    id        BIGSERIAL PRIMARY KEY,
    total_sum NUMERIC(10, 2) NOT NULL CHECK(total_sum >= 0)
);

CREATE TABLE IF NOT EXISTS order_items
(
    id       BIGSERIAL PRIMARY KEY,
    order_id BIGINT         NOT NULL REFERENCES orders (id),
    title    VARCHAR(200)   NOT NULL,
    price    NUMERIC(10, 2) NOT NULL CHECK(price > 0),
    count    INTEGER        NOT NULL CHECK(count > 0)
);

CREATE TABLE IF NOT EXISTS cart_item
(
    id         BIGSERIAL PRIMARY KEY,
    product_id BIGINT  NOT NULL UNIQUE REFERENCES product (id),
    count      INTEGER NOT NULL CHECK(count > 0)
);
