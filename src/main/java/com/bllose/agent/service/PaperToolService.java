package com.bllose.agent.service;

import com.bllose.agent.model.PaperMetadata;
import com.bllose.agent.repository.PaperMetadataRepository;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class PaperToolService {

    private static final Logger log = LoggerFactory.getLogger(PaperToolService.class);

    private final PaperMetadataRepository repo;

    public PaperToolService(PaperMetadataRepository repo) {
        this.repo = repo;
    }

    @Tool("Save a single paper's metadata to the database. Call this for every paper you find or discuss, " +
          "so the user can later browse, search, and download them. If the DOI already exists, the record will be updated.")
    public String savePaperMetadata(
            @P("DOI of the paper (unique identifier, required)") String doi,
            @P("Paper title") String title,
            @P("Comma-separated author names, e.g. 'Smith J, Doe A, Wang L'") String authors,
            @P("Publication year, e.g. 2024") Integer year,
            @P("Journal or venue name, e.g. 'Nature'") String journal,
            @P("Number of citations") Integer citationCount,
            @P("Paper abstract text") String paperAbstract,
            @P("Open access PDF download URL") String pdfUrl,
            @P("BibTeX citation string") String bibtex) {

        if (doi == null || doi.isBlank()) {
            return "Error: DOI is required to save paper metadata.";
        }

        try {
            PaperMetadata m = new PaperMetadata();
            m.setDoi(doi.strip());
            m.setTitle(title != null && !title.isBlank() ? title.strip() : null);
            m.setAuthors(authors != null && !authors.isBlank() ? authors.strip() : null);
            m.setYear(year);
            m.setJournal(journal != null && !journal.isBlank() ? journal.strip() : null);
            m.setCitationCount(citationCount);
            m.setPaperAbstract(paperAbstract != null && !paperAbstract.isBlank() ? paperAbstract.strip() : null);
            m.setPdfUrl(pdfUrl != null && !pdfUrl.isBlank() ? pdfUrl.strip() : null);
            m.setBibtex(bibtex != null && !bibtex.isBlank() ? bibtex.strip() : null);

            boolean existed = repo.existsByDoi(doi.strip());
            repo.save(m);

            log.info("Paper metadata saved: DOI={}, title={}", doi, title);
            return existed
                    ? "Updated existing paper record for DOI: " + doi
                    : "Saved new paper: \"" + (title != null ? title : doi) + "\" to database.";
        } catch (Exception e) {
            log.error("Failed to save paper metadata for DOI={}: {}", doi, e.getMessage());
            return "Failed to save paper: " + e.getMessage();
        }
    }

    @Tool("Search the local database for previously saved papers by keyword (matches title, authors, or abstract). " +
          "Use this when the user asks about papers they have already saved.")
    public String searchSavedPapers(
            @P("Search keyword") String keyword,
            @P("Maximum number of results (default 10)") Integer limit) {

        int max = limit != null && limit > 0 ? Math.min(limit, 50) : 10;

        try {
            var results = repo.searchByKeyword(keyword, max);
            if (results.isEmpty()) {
                return "No saved papers found matching \"" + keyword + "\".";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("Found ").append(results.size()).append(" saved paper(s) matching \"")
                    .append(keyword).append("\":\n\n");
            for (int i = 0; i < results.size(); i++) {
                PaperMetadata p = results.get(i);
                sb.append("## ").append(i + 1).append(". ");
                if (p.getTitle() != null) sb.append(p.getTitle());
                if (p.getYear() != null) sb.append(" (").append(p.getYear()).append(")");
                sb.append("\n");
                if (p.getDoi() != null) sb.append("   DOI: ").append(p.getDoi()).append("\n");
                if (p.getAuthors() != null) sb.append("   Authors: ").append(p.getAuthors()).append("\n");
                if (p.getJournal() != null) sb.append("   Journal: ").append(p.getJournal()).append("\n");
                if (p.getCitationCount() != null) sb.append("   Citations: ").append(p.getCitationCount()).append("\n");
                if (p.getPdfUrl() != null) sb.append("   PDF: ").append(p.getPdfUrl()).append("\n");
                if (p.getPaperAbstract() != null) {
                    String ab = p.getPaperAbstract();
                    if (ab.length() > 300) ab = ab.substring(0, 300) + "...";
                    sb.append("   Abstract: ").append(ab).append("\n");
                }
                sb.append("\n");
            }
            return sb.toString();
        } catch (Exception e) {
            log.error("Search failed: {}", e.getMessage());
            return "Search error: " + e.getMessage();
        }
    }
}
