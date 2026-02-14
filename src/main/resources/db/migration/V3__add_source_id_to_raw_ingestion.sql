-- Add source_id column for deduplication (e.g. Gmail message ID, SMS hash)
ALTER TABLE raw_ingestion ADD COLUMN source_id VARCHAR(255);

CREATE UNIQUE INDEX idx_raw_ingestion_source_id ON raw_ingestion(source_id)
    WHERE source_id IS NOT NULL;
