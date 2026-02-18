-- Add ingested indicator to track which messages have been successfully ingested
ALTER TABLE raw_ingestion ADD COLUMN ingested BOOLEAN NOT NULL DEFAULT FALSE;

-- Create index for efficient filtering of non-ingested records
CREATE INDEX idx_raw_ingestion_ingested ON raw_ingestion(ingested);

-- Mark all existing records as ingested (since they are already in the table)
UPDATE raw_ingestion SET ingested = TRUE;
