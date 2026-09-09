-- ETG V1: consent + messaging source of truth (ADR-002/007)
CREATE TABLE IF NOT EXISTS contacts (
  id BIGSERIAL PRIMARY KEY,
  crm_id VARCHAR(64),
  display_name VARCHAR(255),
  primary_phone VARCHAR(32),
  locale VARCHAR(16) DEFAULT 'en-US',
  timezone VARCHAR(64) DEFAULT 'America/New_York',
  lob VARCHAR(64),
  agent_id VARCHAR(64),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS consents (
  id BIGSERIAL PRIMARY KEY,
  phone_e164 VARCHAR(32) NOT NULL,
  topic VARCHAR(32) NOT NULL, -- servicing|billing|claims|marketing
  status VARCHAR(16) NOT NULL, -- opted_in|opted_out
  source VARCHAR(32) NOT NULL,
  proof_text_version VARCHAR(64) NOT NULL,
  actor VARCHAR(128),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (phone_e164, topic)
);

CREATE TABLE IF NOT EXISTS consent_events (
  id BIGSERIAL PRIMARY KEY,
  phone_e164 VARCHAR(32) NOT NULL,
  topic VARCHAR(32) NOT NULL,
  old_status VARCHAR(16),
  new_status VARCHAR(16) NOT NULL,
  source VARCHAR(32) NOT NULL,
  actor VARCHAR(128),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS messages (
  id BIGSERIAL PRIMARY KEY,
  tenant VARCHAR(64) NOT NULL DEFAULT 'default',
  to_phone VARCHAR(32) NOT NULL,
  from_sender VARCHAR(64),
  channel VARCHAR(16) NOT NULL DEFAULT 'sms',
  topic VARCHAR(32) NOT NULL DEFAULT 'servicing',
  body_hash VARCHAR(128) NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'queued',
  twilio_sid VARCHAR(64),
  idempotency_key VARCHAR(128) NOT NULL UNIQUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS deliveries (
  id BIGSERIAL PRIMARY KEY,
  message_sid VARCHAR(64) NOT NULL,
  status VARCHAR(32) NOT NULL,
  error_code VARCHAR(16),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_deliveries_sid ON deliveries(message_sid);
