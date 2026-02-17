-- Clean Ledger: structured entries parsed from Gmail/SMS (buy, sell, SIP, dividend, etc.)
CREATE TABLE clean_ledger_entry (
    id BIGSERIAL PRIMARY KEY,
    raw_ingestion_id BIGINT REFERENCES raw_ingestion(id),
    entry_type VARCHAR(30) NOT NULL,
    instrument_type VARCHAR(20) NOT NULL,
    symbol VARCHAR(50),
    name VARCHAR(255),
    quantity NUMERIC(20, 6) NOT NULL,
    price NUMERIC(18, 4),
    amount NUMERIC(18, 2) NOT NULL,
    currency VARCHAR(10) NOT NULL DEFAULT 'INR',
    ledger_date TIMESTAMP NOT NULL,
    description TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_clean_ledger_raw_ingestion ON clean_ledger_entry(raw_ingestion_id);
CREATE INDEX idx_clean_ledger_date ON clean_ledger_entry(ledger_date);
CREATE INDEX idx_clean_ledger_instrument_type ON clean_ledger_entry(instrument_type);
CREATE INDEX idx_clean_ledger_entry_type ON clean_ledger_entry(entry_type);

-- Portfolio state: current holdings (stocks / mutual funds) derived from messages
CREATE TABLE portfolio_holding (
    id BIGSERIAL PRIMARY KEY,
    raw_ingestion_id BIGINT REFERENCES raw_ingestion(id),
    instrument_type VARCHAR(20) NOT NULL,
    symbol VARCHAR(50),
    name VARCHAR(255),
    quantity NUMERIC(20, 6) NOT NULL,
    average_price NUMERIC(18, 4),
    current_value NUMERIC(18, 2),
    currency VARCHAR(10) NOT NULL DEFAULT 'INR',
    last_updated TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (instrument_type, symbol)
);

CREATE INDEX idx_portfolio_holding_instrument_type ON portfolio_holding(instrument_type);
CREATE INDEX idx_portfolio_holding_symbol ON portfolio_holding(symbol);
