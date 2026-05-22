CREATE TABLE audit_logs (
    id            BIGSERIAL    PRIMARY KEY,
    action        VARCHAR(32)  NOT NULL,
    entity_type   VARCHAR(32)  NOT NULL,
    entity_id     BIGINT       NOT NULL,
    performed_by  BIGINT       NULL,
    actor         VARCHAR(16)  NOT NULL,
    timestamp     TIMESTAMPTZ  NOT NULL,
    details       JSONB        NULL
);

CREATE INDEX idx_audit_logs_timestamp_desc ON audit_logs (timestamp DESC);
CREATE INDEX idx_audit_logs_entity_type    ON audit_logs (entity_type);
CREATE INDEX idx_audit_logs_entity         ON audit_logs (entity_type, entity_id);
CREATE INDEX idx_audit_logs_performed_by   ON audit_logs (performed_by);
CREATE INDEX idx_audit_logs_action         ON audit_logs (action);
