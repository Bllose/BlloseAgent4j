package com.bllose.agent.repository;

import com.bllose.agent.model.DownloadRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class DownloadRecordRepository {
    private final JdbcTemplate jdbc;

    public DownloadRecordRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private final RowMapper<DownloadRecord> rowMapper = (rs, rowNum) -> new DownloadRecord(
        rs.getLong("id"),
        rs.getString("external_id"),
        rs.getString("id_type"),
        rs.getString("source_url"),
        rs.getString("file_name"),
        rs.getLong("file_size"),
        rs.getTimestamp("created_at").toLocalDateTime()
    );

    public Optional<DownloadRecord> findByExternalId(String externalId) {
        var list = jdbc.query(
            "SELECT * FROM paper_downloads WHERE external_id = ?", rowMapper, externalId);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public DownloadRecord save(DownloadRecord record) {
        jdbc.update(
            "INSERT INTO paper_downloads (external_id, id_type, source_url, file_name, file_size) VALUES (?, ?, ?, ?, ?)",
            record.getExternalId(), record.getIdType(), record.getSourceUrl(),
            record.getFileName(), record.getFileSize());
        return findByExternalId(record.getExternalId()).orElseThrow();
    }

    public boolean existsByExternalId(String externalId) {
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM paper_downloads WHERE external_id = ?", Integer.class, externalId);
        return count != null && count > 0;
    }
}
