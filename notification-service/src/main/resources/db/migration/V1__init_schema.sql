CREATE TABLE notification_log (
    id         BIGSERIAL PRIMARY KEY,
    order_id   BIGINT NOT NULL,
    type       VARCHAR(32) NOT NULL,
    channel    VARCHAR(32) NOT NULL,
    recipient  VARCHAR(255) NOT NULL,
    content    TEXT NOT NULL,
    sent_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_notification_log_order_id ON notification_log(order_id);

CREATE TABLE processed_events (
    id           BIGSERIAL PRIMARY KEY,
    topic        VARCHAR(64) NOT NULL,
    order_id     BIGINT NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (topic, order_id)
);
