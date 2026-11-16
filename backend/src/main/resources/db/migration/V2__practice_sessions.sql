CREATE TABLE practice_session (
 id VARCHAR(255) PRIMARY KEY, package_id VARCHAR NOT NULL REFERENCES exercise_package(id),
 mode VARCHAR(255) NOT NULL, status VARCHAR(255) NOT NULL, current_attempt_id VARCHAR(255), reason VARCHAR(255), created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE TABLE practice_attempt (
 id VARCHAR(255) PRIMARY KEY, session_id VARCHAR(255) NOT NULL REFERENCES practice_session(id),
 exercise_id VARCHAR NOT NULL REFERENCES imported_exercise(id), task_number INTEGER NOT NULL,
 sequence_number INTEGER NOT NULL, status VARCHAR(255) NOT NULL, stage VARCHAR(255) NOT NULL,
 choice VARCHAR(255), automatic_choice BOOLEAN NOT NULL, selection_ms BIGINT NOT NULL,
 preparation_ms BIGINT NOT NULL, duration_ms BIGINT NOT NULL,
 draft_json CLOB, response_json CLOB, result_json CLOB, submission_hash VARCHAR(255),
 audio_path VARCHAR(255), audio_hash VARCHAR(255), audio_bytes BIGINT NOT NULL,
 created_at TIMESTAMP WITH TIME ZONE NOT NULL, completed_at TIMESTAMP WITH TIME ZONE,
 UNIQUE(exercise_id, sequence_number)
);
