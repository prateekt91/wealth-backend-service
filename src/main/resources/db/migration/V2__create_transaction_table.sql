CREATE TABLE transaction (
    id BIGSERIAL PRIMARY KEY,
    raw_ingestion_id BIGINT REFERENCES raw_ingestion(id),
    amount NUMERIC(15, 2) NOT NULL,
    currency VARCHAR(10) NOT NULL DEFAULT 'INR',
    merchant_name VARCHAR(255),
    category VARCHAR(100),
    transaction_type VARCHAR(10) NOT NULL,
    transaction_date TIMESTAMP NOT NULL,
    description TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_transaction_date ON transaction(transaction_date);
CREATE INDEX idx_transaction_category ON transaction(category);
CREATE INDEX idx_transaction_type ON transaction(transaction_type);
CREATE INDEX idx_transaction_raw_ingestion_id ON transaction(raw_ingestion_id);
