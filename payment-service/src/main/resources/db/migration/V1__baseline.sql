--
-- PostgreSQL database dump
--


-- Dumped from database version 16.15
-- Dumped by pg_dump version 16.15




--
-- Name: dispute; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.dispute (
    id uuid NOT NULL,
    amount_units bigint NOT NULL,
    amount_currency character varying(255) NOT NULL,
    merchant_id uuid NOT NULL,
    opened_at timestamp(6) without time zone NOT NULL,
    payment_id uuid NOT NULL,
    reason character varying(255),
    resolved_at timestamp(6) without time zone,
    source_account character varying(30) NOT NULL,
    status character varying(20) NOT NULL,
    CONSTRAINT dispute_source_account_check CHECK (((source_account)::text = ANY ((ARRAY['CUSTOMER_FUNDS'::character varying, 'MERCHANT_PAYABLE'::character varying, 'PLATFORM_FEE'::character varying, 'GST_PAYABLE'::character varying, 'TDS_PAYABLE'::character varying, 'MERCHANT_RESERVE'::character varying, 'DISPUTE_HOLD'::character varying, 'BANK_SETTLEMENT'::character varying, 'PLATFORM_RECEIVABLE'::character varying])::text[]))),
    CONSTRAINT dispute_status_check CHECK (((status)::text = ANY ((ARRAY['OPENED'::character varying, 'UNDER_REVIEW'::character varying, 'WON'::character varying, 'LOST'::character varying])::text[])))
);


--
-- Name: domain_event; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.domain_event (
    id uuid NOT NULL,
    aggregate_id uuid NOT NULL,
    aggregate_type character varying(50) NOT NULL,
    event_type character varying(50) NOT NULL,
    occurred_at timestamp(6) without time zone NOT NULL,
    payload jsonb NOT NULL,
    schema_version integer NOT NULL,
    sequence_number integer NOT NULL
);


--
-- Name: domain_snapshot; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.domain_snapshot (
    aggregate_id uuid NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    sequence_number integer NOT NULL,
    status character varying(30) NOT NULL
);


--
-- Name: fx_rate_lock; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.fx_rate_lock (
    payment_id uuid NOT NULL,
    base_currency character varying(5) NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    expires_at timestamp(6) without time zone NOT NULL,
    quote_currency character varying(5) NOT NULL,
    rate numeric(18,8) NOT NULL,
    spread_bps integer NOT NULL
);


--
-- Name: ledger_line; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.ledger_line (
    id uuid NOT NULL,
    account character varying(30) NOT NULL,
    credit_units bigint NOT NULL,
    currency character varying(5) NOT NULL,
    debit_units bigint NOT NULL,
    posted_at timestamp(6) without time zone NOT NULL,
    reference_id uuid NOT NULL,
    reference_type character varying(30) NOT NULL,
    transaction_id uuid NOT NULL,
    CONSTRAINT ledger_line_account_check CHECK (((account)::text = ANY ((ARRAY['CUSTOMER_FUNDS'::character varying, 'MERCHANT_PAYABLE'::character varying, 'PLATFORM_FEE'::character varying, 'GST_PAYABLE'::character varying, 'TDS_PAYABLE'::character varying, 'MERCHANT_RESERVE'::character varying, 'DISPUTE_HOLD'::character varying, 'BANK_SETTLEMENT'::character varying, 'PLATFORM_RECEIVABLE'::character varying])::text[])))
);

CREATE UNIQUE INDEX ux_ledger_line_txn_account ON public.ledger_line (transaction_id, account);


--
-- Name: order_record; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.order_record (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone,
    created_by character varying(255),
    updated_at timestamp(6) without time zone,
    updated_by character varying(255),
    version bigint NOT NULL,
    amount_units bigint,
    currency character varying(255),
    attempts integer NOT NULL,
    customer_id uuid,
    expires_at timestamp(6) without time zone NOT NULL,
    merchant_id uuid NOT NULL,
    notes jsonb,
    order_status character varying(20) NOT NULL,
    receipt character varying(100),
    CONSTRAINT order_record_order_status_check CHECK (((order_status)::text = ANY ((ARRAY['CREATED'::character varying, 'ATTEMPTED'::character varying, 'PAID'::character varying, 'CANCELLED'::character varying])::text[])))
);


--
-- Name: outbox_event; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.outbox_event (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone,
    created_by character varying(255),
    updated_at timestamp(6) without time zone,
    updated_by character varying(255),
    version bigint NOT NULL,
    aggregate_id uuid NOT NULL,
    aggregate_type character varying(255) NOT NULL,
    attempts integer NOT NULL,
    event_type character varying(50) NOT NULL,
    last_error character varying(1000),
    payload jsonb NOT NULL,
    published_at timestamp(6) without time zone,
    status character varying(255) NOT NULL,
    CONSTRAINT outbox_event_aggregate_type_check CHECK (((aggregate_type)::text = ANY ((ARRAY['PAYMENT'::character varying, 'ORDER'::character varying, 'REFUND'::character varying, 'SETTLEMENT'::character varying])::text[]))),
    CONSTRAINT outbox_event_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'PUBLISHED'::character varying, 'FAILED'::character varying])::text[])))
);


--
-- Name: payment; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.payment (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone,
    created_by character varying(255),
    updated_at timestamp(6) without time zone,
    updated_by character varying(255),
    version bigint NOT NULL,
    amount_units bigint,
    currency character varying(255),
    authorized_at timestamp(6) without time zone,
    bank_reference character varying(100),
    captured_at timestamp(6) without time zone,
    error_code character varying(100),
    error_description character varying(255),
    failed_at timestamp(6) without time zone,
    idempotency_key character varying(100) NOT NULL,
    merchant_id uuid NOT NULL,
    method smallint NOT NULL,
    method_details jsonb,
    processor_reference character varying(100),
    refunded_at timestamp(6) without time zone,
    settled_at timestamp(6) without time zone,
    status character varying(30) NOT NULL,
    order_id uuid NOT NULL,
    CONSTRAINT payment_method_check CHECK (((method >= 0) AND (method <= 3))),
    CONSTRAINT payment_status_check CHECK (((status)::text = ANY ((ARRAY['CREATED'::character varying, 'AUTHORIZING'::character varying, 'AUTHORIZED'::character varying, 'CAPTURING'::character varying, 'CAPTURED'::character varying, 'FAILED'::character varying, 'CANCELLED'::character varying, 'REFUNDED'::character varying, 'PARTIALLY_REFUNDED'::character varying, 'SETTLED'::character varying, 'AUTH_EXPIRED'::character varying])::text[])))
);


--
-- Name: payment_transition_log; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.payment_transition_log (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone,
    created_by character varying(255),
    updated_at timestamp(6) without time zone,
    updated_by character varying(255),
    version bigint NOT NULL,
    actor character varying(100),
    event character varying(30) NOT NULL,
    from_status character varying(30),
    occurred_at timestamp(6) without time zone NOT NULL,
    to_status character varying(30) NOT NULL,
    payment_id uuid NOT NULL,
    CONSTRAINT payment_transition_log_actor_check CHECK (((actor)::text = ANY ((ARRAY['CUSTOMER'::character varying, 'MERCHANT'::character varying, 'SYSTEM'::character varying])::text[]))),
    CONSTRAINT payment_transition_log_event_check CHECK (((event)::text = ANY ((ARRAY['AUTHORIZE_ATTEMPT'::character varying, 'AUTHORIZE_SUCCESS'::character varying, 'AUTHORIZE_FAIL'::character varying, 'CAPTURE_REQUEST'::character varying, 'CAPTURE_SUCCESS'::character varying, 'CAPTURE_FAIL'::character varying, 'REFUND_INIT'::character varying, 'REFUND_COMPLETE'::character varying, 'SETTLE'::character varying, 'CANCEL'::character varying, 'CAPTURE_TIMEOUT'::character varying])::text[]))),
    CONSTRAINT payment_transition_log_from_status_check CHECK (((from_status)::text = ANY ((ARRAY['CREATED'::character varying, 'AUTHORIZING'::character varying, 'AUTHORIZED'::character varying, 'CAPTURING'::character varying, 'CAPTURED'::character varying, 'FAILED'::character varying, 'CANCELLED'::character varying, 'REFUNDED'::character varying, 'PARTIALLY_REFUNDED'::character varying, 'SETTLED'::character varying, 'AUTH_EXPIRED'::character varying])::text[]))),
    CONSTRAINT payment_transition_log_to_status_check CHECK (((to_status)::text = ANY ((ARRAY['CREATED'::character varying, 'AUTHORIZING'::character varying, 'AUTHORIZED'::character varying, 'CAPTURING'::character varying, 'CAPTURED'::character varying, 'FAILED'::character varying, 'CANCELLED'::character varying, 'REFUNDED'::character varying, 'PARTIALLY_REFUNDED'::character varying, 'SETTLED'::character varying, 'AUTH_EXPIRED'::character varying])::text[])))
);


--
-- Name: refund; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.refund (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone,
    created_by character varying(255),
    updated_at timestamp(6) without time zone,
    updated_by character varying(255),
    version bigint NOT NULL,
    amount_units bigint,
    currency character varying(255),
    bank_reference character varying(100),
    error_code character varying(100),
    error_description character varying(500),
    idempotency_key character varying(100) NOT NULL,
    merchant_id uuid NOT NULL,
    notes jsonb,
    processed_at timestamp(6) without time zone,
    reason character varying(255),
    status character varying(20) NOT NULL,
    payment_id uuid NOT NULL,
    CONSTRAINT refund_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'PROCESSING'::character varying, 'PROCESSED'::character varying, 'FAILED'::character varying])::text[])))
);


--
-- Name: reserve_release; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.reserve_release (
    id uuid NOT NULL,
    released_at timestamp(6) without time zone NOT NULL,
    source_line_id uuid NOT NULL,
    transaction_id uuid NOT NULL
);


--
-- Name: dispute dispute_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dispute
    ADD CONSTRAINT dispute_pkey PRIMARY KEY (id);


--
-- Name: domain_event domain_event_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.domain_event
    ADD CONSTRAINT domain_event_pkey PRIMARY KEY (id);


--
-- Name: domain_snapshot domain_snapshot_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.domain_snapshot
    ADD CONSTRAINT domain_snapshot_pkey PRIMARY KEY (aggregate_id);


--
-- Name: fx_rate_lock fx_rate_lock_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.fx_rate_lock
    ADD CONSTRAINT fx_rate_lock_pkey PRIMARY KEY (payment_id);


--
-- Name: order_record idx_order_merchant_receipt; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.order_record
    ADD CONSTRAINT idx_order_merchant_receipt UNIQUE (merchant_id, receipt);


--
-- Name: payment idx_payment_merchant_idempotency; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payment
    ADD CONSTRAINT idx_payment_merchant_idempotency UNIQUE (merchant_id, idempotency_key);


--
-- Name: ledger_line ledger_line_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ledger_line
    ADD CONSTRAINT ledger_line_pkey PRIMARY KEY (id);


--
-- Name: order_record order_record_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.order_record
    ADD CONSTRAINT order_record_pkey PRIMARY KEY (id);


--
-- Name: outbox_event outbox_event_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.outbox_event
    ADD CONSTRAINT outbox_event_pkey PRIMARY KEY (id);


--
-- Name: payment payment_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payment
    ADD CONSTRAINT payment_pkey PRIMARY KEY (id);


--
-- Name: payment_transition_log payment_transition_log_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payment_transition_log
    ADD CONSTRAINT payment_transition_log_pkey PRIMARY KEY (id);


--
-- Name: refund refund_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.refund
    ADD CONSTRAINT refund_pkey PRIMARY KEY (id);


--
-- Name: reserve_release reserve_release_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reserve_release
    ADD CONSTRAINT reserve_release_pkey PRIMARY KEY (id);


--
-- Name: domain_event uk_domain_event_aggregate_sequence; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.domain_event
    ADD CONSTRAINT uk_domain_event_aggregate_sequence UNIQUE (aggregate_id, sequence_number);


--
-- Name: refund uk_refund_payment_idempotency; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.refund
    ADD CONSTRAINT uk_refund_payment_idempotency UNIQUE (payment_id, idempotency_key);


--
-- Name: reserve_release uk_reserve_release_line; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reserve_release
    ADD CONSTRAINT uk_reserve_release_line UNIQUE (source_line_id);


--
-- Name: idx_dispute_merchant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_dispute_merchant ON public.dispute USING btree (merchant_id);


--
-- Name: idx_dispute_payment; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_dispute_payment ON public.dispute USING btree (payment_id);


--
-- Name: idx_domain_event_aggregate; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_domain_event_aggregate ON public.domain_event USING btree (aggregate_id);


--
-- Name: idx_ledger_account; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_ledger_account ON public.ledger_line USING btree (account);


--
-- Name: idx_ledger_reference; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_ledger_reference ON public.ledger_line USING btree (reference_type, reference_id);


--
-- Name: idx_ledger_transaction; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_ledger_transaction ON public.ledger_line USING btree (transaction_id);


--
-- Name: idx_order_id_merchant_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_order_id_merchant_id ON public.order_record USING btree (id, merchant_id);


--
-- Name: idx_order_merchant_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_order_merchant_id ON public.order_record USING btree (merchant_id);


--
-- Name: idx_outbox_status_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_outbox_status_created ON public.outbox_event USING btree (status, created_at);


--
-- Name: idx_payment_merchant_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_payment_merchant_id ON public.payment USING btree (merchant_id);


--
-- Name: idx_payment_order_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_payment_order_id ON public.payment USING btree (order_id);


--
-- Name: idx_payment_status_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_payment_status_created ON public.payment USING btree (status, created_at);


--
-- Name: idx_payment_transition_log_payment_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_payment_transition_log_payment_id ON public.payment_transition_log USING btree (payment_id);


--
-- Name: idx_refund_payment; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_refund_payment ON public.refund USING btree (payment_id);


--
-- Name: payment fk8tny818kg1ue5ajkn040ed8lm; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payment
    ADD CONSTRAINT fk8tny818kg1ue5ajkn040ed8lm FOREIGN KEY (order_id) REFERENCES public.order_record(id);


--
-- Name: refund fkeoh1147brjy6m009cswl5lty4; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.refund
    ADD CONSTRAINT fkeoh1147brjy6m009cswl5lty4 FOREIGN KEY (payment_id) REFERENCES public.payment(id);


--
-- Name: payment_transition_log fki15m2a3cnw0l7p1jfc1pvx15a; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payment_transition_log
    ADD CONSTRAINT fki15m2a3cnw0l7p1jfc1pvx15a FOREIGN KEY (payment_id) REFERENCES public.payment(id);


--
-- PostgreSQL database dump complete
--


