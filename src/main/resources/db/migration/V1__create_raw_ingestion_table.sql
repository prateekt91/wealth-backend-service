CREATE TABLE raw_ingestion (
    id BIGSERIAL PRIMARY KEY,
    source VARCHAR(20) NOT NULL,
    sender_address VARCHAR(255),
    raw_body TEXT NOT NULL,
    received_at TIMESTAMP NOT NULL DEFAULT NOW(),
    processed BOOLEAN NOT NULL DEFAULT FALSE,
    processed_at TIMESTAMP
);

CREATE INDEX idx_raw_ingestion_processed ON raw_ingestion(processed);
CREATE INDEX idx_raw_ingestion_received_at ON raw_ingestion(received_at);
