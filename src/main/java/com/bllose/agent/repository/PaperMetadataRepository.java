package com.bllose.agent.repository;

import com.bllose.agent.model.PaperMetadata;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class PaperMetadataRepository {

    private final JdbcTemplate jdbc;

    public PaperMetadataRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private final RowMapper<PaperMetadata> rowMapper = (rs, rowNum) -> {
        PaperMetadata m = new PaperMetadata();
        m.setId(rs.getLong("id"));
        m.setDoi(rs.getString("doi"));
        m.setTitle(rs.getString("title"));
        m.setAuthors(rs.getString("authors"));
        m.setPublicationDate(rs.getString("publication_date"));
        int year = rs.getInt("year");
        m.setYear(rs.wasNull() ? null : year);
        m.setJournal(rs.getString("journal"));
        m.setVolume(rs.getString("volume"));
        m.setIssue(rs.getString("issue"));
        m.setPages(rs.getString("pages"));
        m.setPublisher(rs.getString("publisher"));
        int citationCount = rs.getInt("citation_count");
        m.setCitationCount(rs.wasNull() ? null : citationCount);
        int infCitationCount = rs.getInt("influential_citation_count");
        m.setInfluentialCitationCount(rs.wasNull() ? null : infCitationCount);
        m.setUrl(rs.getString("url"));
        m.setPdfUrl(rs.getString("pdf_url"));
        m.setBibtex(rs.getString("bibtex"));
        m.setIssn(rs.getString("issn"));
        int openAccess = rs.getInt("is_open_access");
        m.setIsOpenAccess(rs.wasNull() ? null : openAccess == 1);
        m.setPublicationTypes(rs.getString("publication_types"));
        m.setPaperAbstract(rs.getString("abstract"));
        int refCount = rs.getInt("reference_count");
        m.setReferenceCount(rs.wasNull() ? null : refCount);
        m.setRetractionStatus(rs.getString("retraction_status"));
        m.setSourceQuality(rs.getString("source_quality"));
        m.setSourcesJson(rs.getString("sources_json"));
        long downloadRecordId = rs.getLong("download_record_id");
        m.setDownloadRecordId(rs.wasNull() ? null : downloadRecordId);
        m.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        m.setUpdatedAt(rs.getTimestamp("updated_at").toLocalDateTime());
        return m;
    };

    public Optional<PaperMetadata> findByDoi(String doi) {
        var list = jdbc.query(
            "SELECT * FROM paper_metadata WHERE doi = ?", rowMapper, doi);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public boolean existsByDoi(String doi) {
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM paper_metadata WHERE doi = ?", Integer.class, doi);
        return count != null && count > 0;
    }

    public PaperMetadata save(PaperMetadata m) {
        jdbc.update(
            "INSERT INTO paper_metadata (doi, title, authors, publication_date, year, journal, " +
            "volume, issue, pages, publisher, citation_count, influential_citation_count, " +
            "url, pdf_url, bibtex, issn, is_open_access, publication_types, abstract, " +
            "reference_count, retraction_status, source_quality, sources_json, download_record_id) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
            "ON CONFLICT(doi) DO UPDATE SET " +
            "title = excluded.title, authors = excluded.authors, publication_date = excluded.publication_date, " +
            "year = excluded.year, journal = excluded.journal, volume = excluded.volume, " +
            "issue = excluded.issue, pages = excluded.pages, publisher = excluded.publisher, " +
            "citation_count = excluded.citation_count, influential_citation_count = excluded.influential_citation_count, " +
            "url = excluded.url, pdf_url = excluded.pdf_url, bibtex = excluded.bibtex, " +
            "issn = excluded.issn, is_open_access = excluded.is_open_access, " +
            "publication_types = excluded.publication_types, abstract = excluded.abstract, " +
            "reference_count = excluded.reference_count, retraction_status = excluded.retraction_status, " +
            "source_quality = excluded.source_quality, sources_json = excluded.sources_json, " +
            "download_record_id = COALESCE(excluded.download_record_id, paper_metadata.download_record_id), " +
            "updated_at = datetime('now')",
            m.getDoi(), m.getTitle(), m.getAuthors(), m.getPublicationDate(), m.getYear(),
            m.getJournal(), m.getVolume(), m.getIssue(), m.getPages(), m.getPublisher(),
            m.getCitationCount(), m.getInfluentialCitationCount(), m.getUrl(), m.getPdfUrl(),
            m.getBibtex(), m.getIssn(), m.getIsOpenAccess() != null && m.getIsOpenAccess() ? 1 : 0,
            m.getPublicationTypes(), m.getPaperAbstract(), m.getReferenceCount(),
            m.getRetractionStatus(), m.getSourceQuality(), m.getSourcesJson(),
            m.getDownloadRecordId());
        return findByDoi(m.getDoi()).orElseThrow();
    }

    public List<PaperMetadata> findAll(int limit, int offset) {
        return jdbc.query(
            "SELECT * FROM paper_metadata ORDER BY created_at DESC LIMIT ? OFFSET ?",
            rowMapper, limit, offset);
    }

    public int count() {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM paper_metadata", Integer.class);
        return count != null ? count : 0;
    }

    public List<PaperMetadata> searchByKeyword(String keyword, int limit) {
        String like = "%" + keyword + "%";
        return jdbc.query(
            "SELECT * FROM paper_metadata WHERE title LIKE ? OR authors LIKE ? OR abstract LIKE ? " +
            "ORDER BY year DESC LIMIT ?",
            rowMapper, like, like, like, limit);
    }
}
