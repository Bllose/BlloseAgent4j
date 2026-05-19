CREATE TABLE IF NOT EXISTS users (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    username   TEXT    NOT NULL UNIQUE,
    password   TEXT    NOT NULL,
    created_at TEXT    NOT NULL DEFAULT (datetime('now'))
);

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
