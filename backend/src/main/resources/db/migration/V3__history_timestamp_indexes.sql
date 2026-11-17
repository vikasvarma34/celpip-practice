-- Additive indexes only: existing attempts and responses remain unchanged.
CREATE INDEX idx_attempt_created_at ON practice_attempt(created_at);
CREATE INDEX idx_attempt_session_created_at ON practice_attempt(session_id, created_at);
