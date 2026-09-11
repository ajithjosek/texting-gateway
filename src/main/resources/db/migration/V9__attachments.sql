-- ETG V9: claim photo attachments (MMS intake)
CREATE TABLE IF NOT EXISTS claim_attachments (
  id BIGSERIAL PRIMARY KEY,
  phone_e164 VARCHAR(32) NOT NULL,
  claim_number VARCHAR(64),
  media_ref TEXT NOT NULL,
  content_type VARCHAR(64) NOT NULL,
  size_bytes BIGINT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_attachments_phone ON claim_attachments(phone_e164);
CREATE INDEX IF NOT EXISTS idx_attachments_claim ON claim_attachments(claim_number);
