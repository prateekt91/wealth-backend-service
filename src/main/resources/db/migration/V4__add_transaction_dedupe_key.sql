-- Deduplication: same transaction (amount, type, date, merchant) from SMS and email counts once
ALTER TABLE transaction ADD COLUMN dedupe_key VARCHAR(64);

CREATE INDEX idx_transaction_dedupe_key ON transaction(dedupe_key)
    WHERE dedupe_key IS NOT NULL;
