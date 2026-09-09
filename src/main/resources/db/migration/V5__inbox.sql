-- ETG V5: agent inbox (S2/S3)
CREATE TABLE IF NOT EXISTS conversations (
  id BIGSERIAL PRIMARY KEY,
  tenant VARCHAR(64) NOT NULL DEFAULT 'default',
  customer_phone VARCHAR(32) NOT NULL,
  topic VARCHAR(32) NOT NULL DEFAULT 'servicing',
  ref_id VARCHAR(128),
  status VARCHAR(16) NOT NULL DEFAULT 'OPEN',
  assignee_agent_id VARCHAR(128),
  channel VARCHAR(16) NOT NULL DEFAULT 'sms',
  replied BOOLEAN NOT NULL DEFAULT FALSE,
  first_reply_due_at TIMESTAMPTZ NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  closed_at TIMESTAMPTZ,
  csat_score INT
);
CREATE INDEX IF NOT EXISTS idx_conversations_open ON conversations(customer_phone, topic, status);
CREATE INDEX IF NOT EXISTS idx_conversations_assignee ON conversations(assignee_agent_id, status);

CREATE TABLE IF NOT EXISTS conversation_messages (
  id BIGSERIAL PRIMARY KEY,
  conversation_id BIGINT NOT NULL REFERENCES conversations(id),
  direction VARCHAR(8) NOT NULL,
  visibility VARCHAR(16) NOT NULL DEFAULT 'CUSTOMER',
  sender_agent_id VARCHAR(128),
  body TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_conversation_messages_thread
  ON conversation_messages(conversation_id, created_at, id);
