CREATE TABLE exercise_package (
    id VARCHAR PRIMARY KEY,
    module VARCHAR(16) NOT NULL,
    schema_version INTEGER NOT NULL,
    imported_at TIMESTAMP WITH TIME ZONE NOT NULL,
    storage_key VARCHAR(36) NOT NULL UNIQUE,
    fingerprint VARCHAR(64) NOT NULL UNIQUE,
    prompt_fingerprint VARCHAR(64) NOT NULL,
    canonical_json CLOB NOT NULL,
    asset_manifest CLOB NOT NULL
);
CREATE INDEX package_prompt_idx ON exercise_package(prompt_fingerprint);
CREATE TABLE imported_exercise (
    id VARCHAR PRIMARY KEY,
    package_id VARCHAR NOT NULL REFERENCES exercise_package(id),
    task_number INTEGER NOT NULL,
    exercise_order INTEGER NOT NULL,
    fingerprint VARCHAR(64) NOT NULL,
    prompt_fingerprint VARCHAR(64) NOT NULL,
    UNIQUE(package_id, exercise_order)
);
CREATE INDEX exercise_fingerprint_idx ON imported_exercise(fingerprint);
CREATE INDEX exercise_prompt_idx ON imported_exercise(prompt_fingerprint);
