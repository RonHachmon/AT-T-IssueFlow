-- Partial index supporting the auto-escalation scheduler scan for candidates:
-- active, not-DONE tickets that carry a due date.
CREATE INDEX ix_tickets_escalation_candidates
    ON tickets (due_date)
    WHERE deleted_at IS NULL AND status <> 'DONE' AND due_date IS NOT NULL;
