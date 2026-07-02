package com.app.billing.config;

import com.app.billing.util.TemplateGeneratorUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Initializes missing Word templates on application startup.
 * Runs after the application context is loaded.
 * 
 * Note: Templates are generated in src/main/resources/templates/ directory.
 * After generation, restart the application or rebuild to ensure templates are in classpath.
 */
@Slf4j
@Component
@Order(1)
public class TemplateInitializer implements CommandLineRunner {
    
    @Override
    public void run(String... args) {
        try {
            log.info("Initializing Word templates...");
            log.info("Working directory: {}", System.getProperty("user.dir"));
            TemplateGeneratorUtil.main(new String[0]);
            log.info("Template initialization completed");
            log.info("⚠️  If templates were just generated, please rebuild the project to ensure they are in classpath.");
        } catch (Exception e) {
            log.error("Failed to initialize templates: {}", e.getMessage(), e);
            log.warn("⚠️  Some templates may be missing. PDF generation may fail.");
            log.warn("⚠️  Run TemplateGeneratorUtil.main() manually to generate templates.");
        }
    }
}

