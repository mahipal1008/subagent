package com.subscrub.controller;

import com.subscrub.service.CsvIngestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class UploadController {

    private final CsvIngestionService ingestionService;

    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("userId") String userId) {

        int count = ingestionService.ingest(file, userId);
        return ResponseEntity.ok(Map.of(
            "imported", count,
            "message", "Detected " + count + " transactions"
        ));
    }
}
