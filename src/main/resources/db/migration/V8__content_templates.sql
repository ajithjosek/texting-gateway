-- ETG V8: Twilio Content API template mirror (WhatsApp/RCS)
CREATE TABLE IF NOT EXISTS content_templates (
  id BIGSERIAL PRIMARY KEY,
  template_key VARCHAR(128) NOT NULL,
  channel VARCHAR(16) NOT NULL DEFAULT 'whatsapp',
  content_sid VARCHAR(64) NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'pending',
  active BOOLEAN NOT NULL DEFAULT TRUE,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_content_templates_sid ON content_templates(content_sid, active);
