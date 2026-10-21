CREATE TABLE webhook_delivery (
 id BIGSERIAL PRIMARY KEY, subscription_ref VARCHAR(80) NOT NULL, event_type VARCHAR(80) NOT NULL,
 scope VARCHAR(120) NOT NULL, payload TEXT NOT NULL, response_code INTEGER NOT NULL,
 attempted_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
