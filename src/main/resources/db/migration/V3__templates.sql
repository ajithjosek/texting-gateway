-- ETG V3: versioned message templates (S2)
CREATE TABLE IF NOT EXISTS templates (
  id BIGSERIAL PRIMARY KEY,
  template_key VARCHAR(128) NOT NULL,
  locale VARCHAR(16) NOT NULL,
  version INT NOT NULL,
  body TEXT NOT NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (template_key, locale, version)
);
CREATE INDEX IF NOT EXISTS idx_templates_active ON templates(template_key, locale) WHERE active;
