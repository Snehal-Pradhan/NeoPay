-- Chargeback closure: evidence window, representment, outcome reason.

ALTER TABLE dispute ADD COLUMN IF NOT EXISTS evidence_due_at timestamp(6) without time zone;
ALTER TABLE dispute ADD COLUMN IF NOT EXISTS evidence_submitted_at timestamp(6) without time zone;
ALTER TABLE dispute ADD COLUMN IF NOT EXISTS evidence_reference character varying(128);
ALTER TABLE dispute ADD COLUMN IF NOT EXISTS outcome_reason character varying(255);

ALTER TABLE dispute DROP CONSTRAINT IF EXISTS dispute_status_check;
ALTER TABLE dispute ADD CONSTRAINT dispute_status_check CHECK (
    (status)::text = ANY ((ARRAY[
        'OPENED'::character varying,
        'EVIDENCE_REQUIRED'::character varying,
        'UNDER_REVIEW'::character varying,
        'REPRESENTED'::character varying,
        'WON'::character varying,
        'LOST'::character varying
    ])::text[])
);

CREATE TABLE IF NOT EXISTS dispute_evidence (
    id uuid NOT NULL,
    dispute_id uuid NOT NULL,
    external_evidence_id character varying(128) NOT NULL,
    summary character varying(500) NOT NULL,
    submitted_at timestamp(6) without time zone NOT NULL,
    CONSTRAINT dispute_evidence_pkey PRIMARY KEY (id),
    CONSTRAINT uq_dispute_evidence_external UNIQUE (dispute_id, external_evidence_id)
);

CREATE INDEX IF NOT EXISTS idx_dispute_evidence_dispute ON dispute_evidence (dispute_id);
CREATE INDEX IF NOT EXISTS idx_dispute_status_due ON dispute (status, evidence_due_at);
