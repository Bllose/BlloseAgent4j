CREATE TABLE IF NOT EXISTS users (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    username    TEXT    NOT NULL UNIQUE,
    password    TEXT    NOT NULL,
    user_number INTEGER,
    created_at  TEXT    NOT NULL DEFAULT (datetime('now'))
);

-- Populate user_number for existing users
ALTER TABLE users ADD COLUMN user_number INTEGER;
UPDATE users SET user_number = 100000 + id WHERE user_number IS NULL;

CREATE TABLE IF NOT EXISTS paper_downloads (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    external_id TEXT    NOT NULL UNIQUE,
    id_type     TEXT    NOT NULL DEFAULT 'DOI',
    source_url  TEXT    NOT NULL DEFAULT '',
    file_name   TEXT    NOT NULL,
    file_size   INTEGER DEFAULT 0,
    created_at  TEXT    NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS paper_metadata (
    id                        INTEGER PRIMARY KEY AUTOINCREMENT,
    doi                       TEXT    NOT NULL UNIQUE,
    title                     TEXT,
    authors                   TEXT,
    publication_date          TEXT,
    year                      INTEGER,
    journal                   TEXT,
    volume                    TEXT,
    issue                     TEXT,
    pages                     TEXT,
    publisher                 TEXT,
    citation_count            INTEGER,
    influential_citation_count INTEGER,
    url                       TEXT,
    pdf_url                   TEXT,
    bibtex                    TEXT,
    issn                      TEXT,
    is_open_access            INTEGER DEFAULT 0,
    publication_types         TEXT,
    abstract                  TEXT,
    reference_count           INTEGER,
    retraction_status         TEXT,
    source_quality            TEXT,
    sources_json              TEXT,
    download_record_id        INTEGER,
    created_at                TEXT    NOT NULL DEFAULT (datetime('now')),
    updated_at                TEXT    NOT NULL DEFAULT (datetime('now'))
);

-- Add source_url to tables created before this column was introduced.
-- Ignored if column already exists (spring.sql.init.continue-on-error=true).
ALTER TABLE paper_downloads ADD COLUMN source_url TEXT NOT NULL DEFAULT '';

CREATE TABLE IF NOT EXISTS user_fingerprints (
    user_id          INTEGER NOT NULL,
    fingerprint_hash TEXT    NOT NULL,
    created_at       TEXT    NOT NULL DEFAULT (datetime('now')),
    PRIMARY KEY (user_id, fingerprint_hash),
    FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS guest_sessions (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    fingerprint     TEXT    NOT NULL UNIQUE,
    fingerprint_hash TEXT   NOT NULL DEFAULT '',
    guest_name      TEXT    NOT NULL DEFAULT 'Guest',
    first_login     TEXT    NOT NULL,
    last_login      TEXT    NOT NULL,
    auth_expiry     TEXT    NOT NULL,
    last_session    TEXT    NOT NULL DEFAULT '',
    request_count   INTEGER NOT NULL DEFAULT 0,
    last_visits     TEXT    NOT NULL DEFAULT '[]',
    created_at      TEXT    NOT NULL DEFAULT (datetime('now')),
    updated_at      TEXT    NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS conversations (
    chat_id     TEXT NOT NULL PRIMARY KEY,
    user_number INTEGER NOT NULL,
    title       TEXT NOT NULL DEFAULT '',
    created_at  TEXT NOT NULL DEFAULT (datetime('now')),
    FOREIGN KEY (user_number) REFERENCES users(user_number)
);
CREATE INDEX IF NOT EXISTS idx_conv_user ON conversations(user_number, created_at DESC);

CREATE TABLE IF NOT EXISTS chat_messages (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    chat_id     TEXT NOT NULL,
    turn_num    INTEGER NOT NULL,
    type        TEXT NOT NULL CHECK(type IN ('user', 'ai')),
    thinking    TEXT,
    message     TEXT NOT NULL,
    created_at  TEXT NOT NULL DEFAULT (datetime('now')),
    FOREIGN KEY (chat_id) REFERENCES conversations(chat_id)
);
CREATE INDEX IF NOT EXISTS idx_msg_chat ON chat_messages(chat_id, turn_num);

CREATE TABLE IF NOT EXISTS message_feedback (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    chat_id         TEXT NOT NULL,
    turn_num        INTEGER NOT NULL,
    user_identifier TEXT NOT NULL,
    user_type       TEXT NOT NULL CHECK(user_type IN ('registered', 'guest')),
    rating          TEXT CHECK(rating IN ('up', 'down')),
    feedback_text   TEXT,
    message_history TEXT,
    created_at      TEXT NOT NULL DEFAULT (datetime('now')),
    updated_at      TEXT NOT NULL DEFAULT (datetime('now')),
    FOREIGN KEY (chat_id) REFERENCES conversations(chat_id)
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_feedback_ck ON message_feedback(chat_id, turn_num);
