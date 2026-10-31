-- Durable inbound PSP event log. The unique key is the replay guard.
CREATE TABLE IF NOT EXISTS webhook_event (
    id uuid NOT NULL,
    provider character varying(32) NOT NULL,
    external_event_id character varying(128) NOT NULL,
    event_type character varying(64) NOT NULL,
    payload text NOT NULL,
    status character varying(16) NOT NULL,
    received_at timestamp(6) without time zone NOT NULL,
    processed_at timestamp(6) without time zone,
    error character varying(255),
    CONSTRAINT webhook_event_pkey PRIMARY KEY (id),
    CONSTRAINT uq_webhook_provider_event UNIQUE (provider, external_event_id)
);

CREATE INDEX IF NOT EXISTS idx_webhook_status_received
    ON webhook_event (status, received_at);
