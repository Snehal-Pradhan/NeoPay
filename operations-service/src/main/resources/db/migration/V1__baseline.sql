--
-- PostgreSQL database dump
--


-- Dumped from database version 16.15
-- Dumped by pg_dump version 16.15




--
-- Name: dlq_event; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.dlq_event (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone,
    created_by character varying(255),
    updated_at timestamp(6) without time zone,
    updated_by character varying(255),
    version bigint NOT NULL,
    final_error character varying(1000),
    merchant_id uuid NOT NULL,
    moved_at timestamp(6) without time zone,
    payload jsonb NOT NULL,
    replayed_at timestamp(6) without time zone,
    webhook_event_id uuid
);


--
-- Name: merchant_fee_schedule; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.merchant_fee_schedule (
    merchant_id uuid NOT NULL,
    fee_bps integer NOT NULL,
    gst_bps integer NOT NULL,
    reserve_bps integer NOT NULL,
    tds_bps integer NOT NULL
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
-- Name: payout; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.payout (
    id uuid NOT NULL,
    amount_units bigint NOT NULL,
    amount_currency character varying(255) NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    file_reference character varying(100),
    merchant_id uuid NOT NULL,
    return_reason character varying(255),
    returned_at timestamp(6) without time zone,
    settlement_id uuid NOT NULL,
    status character varying(20) NOT NULL,
    CONSTRAINT payout_status_check CHECK (((status)::text = ANY ((ARRAY['INITIATED'::character varying, 'SENT'::character varying, 'RETURNED'::character varying])::text[])))
);


--
-- Name: recon_break; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.recon_break (
    id uuid NOT NULL,
    bank_amount bigint,
    created_at timestamp(6) without time zone NOT NULL,
    currency character varying(5),
    internal_amount bigint,
    note character varying(500),
    psp_amount bigint,
    reference character varying(100) NOT NULL,
    run_id uuid NOT NULL,
    status character varying(20) NOT NULL,
    type character varying(30) NOT NULL,
    CONSTRAINT recon_break_status_check CHECK (((status)::text = ANY ((ARRAY['OPEN'::character varying, 'RESOLVED'::character varying])::text[]))),
    CONSTRAINT recon_break_type_check CHECK (((type)::text = ANY ((ARRAY['MISSING_INTERNAL'::character varying, 'MISSING_PSP'::character varying, 'MISSING_BANK'::character varying, 'AMOUNT_MISMATCH_PSP'::character varying, 'AMOUNT_MISMATCH_BANK'::character varying, 'CURRENCY_MISMATCH'::character varying, 'DUPLICATE'::character varying])::text[])))
);


--
-- Name: recon_run; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.recon_run (
    id uuid NOT NULL,
    bank_count integer NOT NULL,
    break_count integer NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    internal_count integer NOT NULL,
    psp_count integer NOT NULL,
    status character varying(20) NOT NULL,
    CONSTRAINT recon_run_status_check CHECK (((status)::text = ANY ((ARRAY['COMPLETED'::character varying, 'FAILED'::character varying])::text[])))
);


--
-- Name: settlement; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.settlement (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone,
    created_by character varying(255),
    updated_at timestamp(6) without time zone,
    updated_by character varying(255),
    version bigint NOT NULL,
    bank_reference character varying(50) NOT NULL,
    carry_forward_units bigint NOT NULL,
    carry_forward_currency character varying(255) NOT NULL,
    failure_reason character varying(255),
    fee_amount_units bigint NOT NULL,
    fee_amount_currency character varying(255) NOT NULL,
    gross_amount_units bigint NOT NULL,
    gross_amount_currency character varying(255) NOT NULL,
    gst_amount_units bigint NOT NULL,
    gst_amount_currency character varying(255) NOT NULL,
    merchant_id uuid NOT NULL,
    net_amount_units bigint NOT NULL,
    net_amount_currency character varying(255) NOT NULL,
    processed_at timestamp(6) without time zone,
    refund_amount_units bigint NOT NULL,
    refund_amount_currency character varying(255) NOT NULL,
    reserve_amount_units bigint NOT NULL,
    reserve_amount_currency character varying(255) NOT NULL,
    status character varying(20) NOT NULL,
    tds_amount_units bigint NOT NULL,
    tds_amount_currency character varying(255) NOT NULL,
    CONSTRAINT settlement_status_check CHECK (((status)::text = ANY ((ARRAY['INITIATED'::character varying, 'TRANSFER_PENDING'::character varying, 'PROCESSED'::character varying, 'FAILED'::character varying])::text[])))
);


--
-- Name: settlement_carry_forward; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.settlement_carry_forward (
    merchant_id uuid NOT NULL,
    currency character varying(5) NOT NULL,
    outstanding_units bigint NOT NULL,
    updated_at timestamp(6) without time zone NOT NULL
);


--
-- Name: settlement_payment; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.settlement_payment (
    payment_id uuid NOT NULL,
    created_at timestamp(6) without time zone,
    created_by character varying(255),
    updated_at timestamp(6) without time zone,
    updated_by character varying(255),
    version bigint NOT NULL,
    settlement_id uuid NOT NULL
);


--
-- Name: webhook_event; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.webhook_event (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone,
    created_by character varying(255),
    updated_at timestamp(6) without time zone,
    updated_by character varying(255),
    version bigint NOT NULL,
    attempts integer NOT NULL,
    delivered_at timestamp(6) without time zone,
    event_type character varying(100) NOT NULL,
    last_attempt_at timestamp(6) without time zone,
    last_response_body character varying(1000),
    last_response_code integer,
    merchant_id uuid NOT NULL,
    next_retry_at timestamp(6) without time zone,
    payload jsonb,
    signature character varying(255) NOT NULL,
    status character varying(255) NOT NULL,
    target_url character varying(255) NOT NULL,
    CONSTRAINT webhook_event_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'DELIVERED'::character varying, 'FAILED'::character varying, 'DEAD'::character varying])::text[])))
);


--
-- Name: dlq_event dlq_event_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dlq_event
    ADD CONSTRAINT dlq_event_pkey PRIMARY KEY (id);


--
-- Name: merchant_fee_schedule merchant_fee_schedule_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.merchant_fee_schedule
    ADD CONSTRAINT merchant_fee_schedule_pkey PRIMARY KEY (merchant_id);


--
-- Name: outbox_event outbox_event_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.outbox_event
    ADD CONSTRAINT outbox_event_pkey PRIMARY KEY (id);


--
-- Name: payout payout_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payout
    ADD CONSTRAINT payout_pkey PRIMARY KEY (id);


--
-- Name: recon_break recon_break_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.recon_break
    ADD CONSTRAINT recon_break_pkey PRIMARY KEY (id);


--
-- Name: recon_run recon_run_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.recon_run
    ADD CONSTRAINT recon_run_pkey PRIMARY KEY (id);


--
-- Name: settlement_carry_forward settlement_carry_forward_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.settlement_carry_forward
    ADD CONSTRAINT settlement_carry_forward_pkey PRIMARY KEY (merchant_id);


--
-- Name: settlement_payment settlement_payment_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.settlement_payment
    ADD CONSTRAINT settlement_payment_pkey PRIMARY KEY (payment_id, settlement_id);


--
-- Name: settlement settlement_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.settlement
    ADD CONSTRAINT settlement_pkey PRIMARY KEY (id);


--
-- Name: dlq_event uk2rnmlgvax847c3ws5yhtp1hgs; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dlq_event
    ADD CONSTRAINT uk2rnmlgvax847c3ws5yhtp1hgs UNIQUE (webhook_event_id);


--
-- Name: payout uk_payout_settlement; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payout
    ADD CONSTRAINT uk_payout_settlement UNIQUE (settlement_id);


--
-- Name: webhook_event webhook_event_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.webhook_event
    ADD CONSTRAINT webhook_event_pkey PRIMARY KEY (id);


--
-- Name: idx_outbox_status_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_outbox_status_created ON public.outbox_event USING btree (status, created_at);


--
-- Name: idx_recon_break_run; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_recon_break_run ON public.recon_break USING btree (run_id);


--
-- Name: settlement_payment fk11x8ihxqap99rjvw65eitmmd; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.settlement_payment
    ADD CONSTRAINT fk11x8ihxqap99rjvw65eitmmd FOREIGN KEY (settlement_id) REFERENCES public.settlement(id);


--
-- Name: dlq_event fkqv72xi3tag231hjexxkni4q6a; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dlq_event
    ADD CONSTRAINT fkqv72xi3tag231hjexxkni4q6a FOREIGN KEY (webhook_event_id) REFERENCES public.webhook_event(id);


--
-- PostgreSQL database dump complete
--


