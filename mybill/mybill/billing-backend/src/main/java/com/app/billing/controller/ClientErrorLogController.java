package com.app.billing.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Receives client-side (frontend) errors and logs them to the backend log files.
 * This way all errors (backend + frontend) appear in the same logs folder.
 */
@RestController
@RequestMapping("/log-client-error")
public class ClientErrorLogController {

    private static final Logger logger = LoggerFactory.getLogger(ClientErrorLogController.class);

    @PostMapping
    public ResponseEntity<Void> logError(@RequestBody Map<String, Object> body) {
        String message = body != null && body.containsKey("message") ? String.valueOf(body.get("message")) : "Unknown";
        String stack = body != null && body.containsKey("stack") ? String.valueOf(body.get("stack")) : null;
        String url = body != null && body.containsKey("url") ? String.valueOf(body.get("url")) : null;

        logger.error("[FRONTEND ERROR] message={} url={} stack={}", message, url, stack != null ? stack : "");
        return ResponseEntity.ok().build();
    }
}
