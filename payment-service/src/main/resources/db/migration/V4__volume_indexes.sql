-- Indexes justified by docs/db/volume-plans.txt on a 1M-row ledger / 94k-payment dataset.
-- Settlement scans by (merchant, status, captured_at) and the outbox poller reads only
-- PENDING rows; both had to filter after a narrower index scan.

CREATE INDEX IF NOT EXISTS idx_payment_merchant_status_captured
    ON payment (merchant_id, status, captured_at);

CREATE INDEX IF NOT EXISTS idx_outbox_pending_created
    ON outbox_event (created_at)
    WHERE status = 'PENDING';
