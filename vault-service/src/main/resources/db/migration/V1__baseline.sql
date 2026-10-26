--
-- PostgreSQL database dump
--


-- Dumped from database version 16.15
-- Dumped by pg_dump version 16.15




--
-- Name: card_token; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.card_token (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone,
    created_by character varying(255),
    updated_at timestamp(6) without time zone,
    updated_by character varying(255),
    version bigint NOT NULL,
    customer uuid,
    merchant uuid NOT NULL,
    revoked_at timestamp(6) without time zone,
    token character varying(50) NOT NULL,
    vault_card_id uuid NOT NULL
);


--
-- Name: vault_card; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.vault_card (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone,
    created_by character varying(255),
    updated_at timestamp(6) without time zone,
    updated_by character varying(255),
    version bigint NOT NULL,
    bin character varying(6) NOT NULL,
    brand character varying(255) NOT NULL,
    card_holder_name character varying(255) NOT NULL,
    deleted_at timestamp(6) without time zone,
    encrypted_dek bytea NOT NULL,
    encrypted_pan bytea NOT NULL,
    expiry_month character varying(255) NOT NULL,
    expiry_year character varying(255) NOT NULL,
    last_four character varying(4) NOT NULL,
    CONSTRAINT vault_card_brand_check CHECK (((brand)::text = ANY ((ARRAY['VISA'::character varying, 'MASTERCARD'::character varying, 'RUPAY'::character varying, 'AMEX'::character varying])::text[])))
);


--
-- Name: card_token card_token_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.card_token
    ADD CONSTRAINT card_token_pkey PRIMARY KEY (id);


--
-- Name: card_token uk53dm4jjlxyrmypgcuohutmw1j; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.card_token
    ADD CONSTRAINT uk53dm4jjlxyrmypgcuohutmw1j UNIQUE (token);


--
-- Name: vault_card vault_card_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.vault_card
    ADD CONSTRAINT vault_card_pkey PRIMARY KEY (id);


--
-- Name: card_token fkfcuhwnqaybadjnid0pigv3n0g; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.card_token
    ADD CONSTRAINT fkfcuhwnqaybadjnid0pigv3n0g FOREIGN KEY (vault_card_id) REFERENCES public.vault_card(id);


--
-- PostgreSQL database dump complete
--


