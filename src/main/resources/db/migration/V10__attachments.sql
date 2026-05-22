CREATE TABLE attachments (
  id           BIGSERIAL PRIMARY KEY,
  ticket_id    BIGINT NOT NULL REFERENCES tickets(id),
  filename     VARCHAR(255) NOT NULL,
  content_type VARCHAR(100) NOT NULL,
  size_bytes   BIGINT NOT NULL,
  storage_key  VARCHAR(512) NOT NULL UNIQUE,
  uploaded_by  BIGINT NOT NULL REFERENCES users(id),
  uploaded_at  TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_attachments_ticket_id ON attachments(ticket_id);
