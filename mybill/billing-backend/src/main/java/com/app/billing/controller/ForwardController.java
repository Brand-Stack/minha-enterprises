package com.app.billing.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Controller to forward Angular routes to index.html.
 * This enables Angular's client-side routing in production/local deployment.
 * 
 * Only active when 'local' profile is enabled (when backend serves frontend).
 * 
 * Routes that should NOT be forwarded:
 * - /api/** (REST API endpoints - handled by ApiPrefixFilter)
 * - Static files (*.js, *.css, *.ico, etc. - handled by StaticResourceConfig)
 */
@Controller
@Profile("local")
public class ForwardController {
    
    @Value("${app.frontend.path:./frontend}")
    private String frontendPath;
    
    /**
     * Serve index.html for the root path and all Angular routes.
     * Angular router will handle the actual routing on the client side.
     */
    @GetMapping(value = {
        "/",
        "/login",
        "/dashboard",
        "/dashboard/**",
        "/billing/**",
        "/master/**",
        "/reports/**",
        "/courier-quotations",
        "/courier-quotations/**",
        "/settings/**",
        "/employees/**",
        "/purchase-expense/**",
        "/print-book/**",
        "/inventory/**"
    })
    @ResponseBody
    public ResponseEntity<Resource> forwardToAngular() throws IOException {
        // Resolve to absolute path so it works regardless of working directory
        Path basePath = Paths.get(frontendPath).toAbsolutePath().normalize();
        Path indexPath = basePath.resolve("index.html");
        Resource resource = new UrlResource(indexPath.toUri());
        
        if (resource.exists() && resource.isReadable()) {
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_HTML)
                    .body(resource);
        } else {
            return ResponseEntity.notFound().build();
        }
    }
}
