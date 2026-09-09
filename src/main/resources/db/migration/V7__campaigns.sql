-- ETG V7: bulk campaigns with approval gate (S6)
CREATE TABLE IF NOT EXISTS campaigns (
  id BIGSERIAL PRIMARY KEY,
  tenant VARCHAR(64) NOT NULL DEFAULT 'default',
  name VARCHAR(255) NOT NULL,
  topic VARCHAR(32) NOT NULL DEFAULT 'marketing',
  template_key VARCHAR(128) NOT NULL,
  locale VARCHAR(16) NOT NULL DEFAULT 'en',
  vars_json TEXT NOT NULL DEFAULT '{}',
  status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
  created_by VARCHAR(128),
  approved_by VARCHAR(128),
  sent_count INT NOT NULL DEFAULT 0,
  skipped_count INT NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS campaign_recipients (
  id BIGSERIAL PRIMARY KEY,
  campaign_id BIGINT NOT NULL REFERENCES campaigns(id),
  phone_e164 VARCHAR(32) NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
  skip_reason VARCHAR(255),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_campaign_recipients_campaign
  ON campaign_recipients(campaign_id, id);
