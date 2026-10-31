-- Method-specific pricing with effectivity dates.
CREATE TABLE IF NOT EXISTS merchant_method_pricing (
    id uuid NOT NULL,
    merchant_id uuid NOT NULL,
    payment_method character varying(20) NOT NULL,
    fee_bps integer NOT NULL,
    fixed_fee_units bigint NOT NULL,
    gst_bps integer NOT NULL,
    tds_bps integer NOT NULL,
    reserve_bps integer NOT NULL,
    max_fee_units bigint NOT NULL,
    effective_from date NOT NULL,
    effective_to date,
    CONSTRAINT merchant_method_pricing_pkey PRIMARY KEY (id),
    CONSTRAINT uq_method_pricing_window UNIQUE (merchant_id, payment_method, effective_from)
);

CREATE INDEX IF NOT EXISTS idx_method_pricing_lookup
    ON merchant_method_pricing (merchant_id, effective_from);
