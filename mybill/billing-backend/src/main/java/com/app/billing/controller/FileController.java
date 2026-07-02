package com.app.billing.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Base64;

@Slf4j
@RestController
@RequestMapping("/files")
@RequiredArgsConstructor
@Tag(name = "File Management", description = "File upload and download operations")
public class FileController {

    @PostMapping("/upload")
    @Operation(summary = "Upload file", description = "Upload a file and return Base64 encoded data")
    public ResponseEntity<String> uploadFile(@RequestParam("file") MultipartFile file) {
        try {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body("File is empty");
            }

            // Convert file to Base64
            byte[] fileBytes = file.getBytes();
            String base64Data = Base64.getEncoder().encodeToString(fileBytes);
            
            // Return Base64 data with metadata
            String result = "data:" + file.getContentType() + ";base64," + base64Data;
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error uploading file", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error uploading file: " + e.getMessage());
        }
    }

    @GetMapping("/download")
    @Operation(summary = "Download file", description = "Download a file from Base64 data")
    public ResponseEntity<byte[]> downloadFile(@RequestParam String data) {
        try {
            // Parse data URI: "data:contentType;base64,base64Data"
            String[] parts = data.split(",");
            if (parts.length != 2) {
                return ResponseEntity.badRequest().build();
            }

            String headerPart = parts[0];
            String base64Data = parts[1];

            // Extract content type
            String contentType = "application/octet-stream";
            if (headerPart.contains(";")) {
                String[] headerParts = headerPart.split(";");
                if (headerParts.length > 0 && headerParts[0] != null) {
                    String[] typeParts = headerParts[0].split(":");
                    if (typeParts.length > 1 && typeParts[1] != null) {
                        contentType = typeParts[1].trim();
                        if (contentType.isEmpty()) {
                            contentType = "application/octet-stream";
                        }
                    }
                }
            }

            // Decode Base64
            byte[] fileBytes = Base64.getDecoder().decode(base64Data);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(contentType));
            headers.setContentDispositionFormData("attachment", "file");

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(fileBytes);
        } catch (Exception e) {
            log.error("Error downloading file", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}

