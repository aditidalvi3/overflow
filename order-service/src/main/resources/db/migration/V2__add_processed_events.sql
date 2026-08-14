CREATE TABLE processed_events (
    id           BIGSERIAL PRIMARY KEY,
    topic        VARCHAR(64) NOT NULL,
    order_id     BIGINT NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (topic, order_id)
);
