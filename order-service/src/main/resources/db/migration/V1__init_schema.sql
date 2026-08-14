CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role          VARCHAR(32) NOT NULL DEFAULT 'CUSTOMER',
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE orders (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id),
    status          VARCHAR(32) NOT NULL,
    total_cents     BIGINT NOT NULL CHECK (total_cents >= 0),
    correlation_id  VARCHAR(36) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_orders_user_id ON orders(user_id);

-- product_id intentionally has no FK: the products table now lives in inventory-service's
-- own database (database-per-service), so order-service can only reference the id by value.
CREATE TABLE order_items (
    id               BIGSERIAL PRIMARY KEY,
    order_id         BIGINT NOT NULL REFERENCES orders(id),
    product_id       BIGINT NOT NULL,
    quantity         INTEGER NOT NULL CHECK (quantity > 0),
    unit_price_cents BIGINT NOT NULL CHECK (unit_price_cents >= 0)
);

CREATE INDEX idx_order_items_order_id ON order_items(order_id);
