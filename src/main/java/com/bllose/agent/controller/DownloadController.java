package com.bllose.agent.controller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/downloads")
public class DownloadController {

    @Value("${app.download.dir:./downloads}")
    private String downloadDir;

    @GetMapping("/{filename}")
    public ResponseEntity<Resource> download(@PathVariable String filename) throws IOException {
        Path dir = Path.of(downloadDir).toAbsolutePath().normalize();
        Path file = dir.resolve(filename).normalize();

        if (!file.startsWith(dir) || !Files.exists(file) || !Files.isReadable(file)) {
            return ResponseEntity.notFound().build();
        }

        Resource resource = new InputStreamResource(Files.newInputStream(file));
        String mimeType = Files.probeContentType(file);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\"")
                .contentType(mimeType != null
                        ? MediaType.parseMediaType(mimeType)
                        : MediaType.APPLICATION_PDF)
                .body(resource);
    }
}
