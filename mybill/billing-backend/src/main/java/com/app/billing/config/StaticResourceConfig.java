package com.app.billing.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.File;

/**
 * Configuration for serving Angular static files in local/production deployment.
 * This is only active when 'local' profile is enabled.
 * 
 * In local deployment mode:
 * - Backend serves the pre-built Angular frontend as static files
 * - No separate Angular dev server needed
 * - Client accesses everything through single port (8080)
 */
@Configuration
@Profile("local")
public class StaticResourceConfig implements WebMvcConfigurer {
    
    @Value("${app.frontend.path:./frontend}")
    private String frontendPath;
    
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Resolve absolute path for the frontend directory
        File frontendDir = new File(frontendPath);
        String absolutePath = frontendDir.getAbsolutePath();
        
        // Ensure path ends with separator
        if (!absolutePath.endsWith(File.separator)) {
            absolutePath += File.separator;
        }
        
        String resourceLocation = "file:" + absolutePath;
        
        System.out.println("===========================================");
        System.out.println("Static Resource Configuration (Local Mode)");
        System.out.println("Frontend Path: " + resourceLocation);
        System.out.println("===========================================");
        
        // Serve index.html with no-cache so browsers always fetch fresh versioning references
        registry.addResourceHandler("/index.html", "/*.html")
                .addResourceLocations(resourceLocation)
                .setCacheControl(org.springframework.http.CacheControl.noCache().noStore().mustRevalidate())
                .resourceChain(true);

        // Serve static files (JS, CSS, fonts, images) with cache support
        registry.addResourceHandler("/**")
                .addResourceLocations(resourceLocation)
                .setCacheControl(org.springframework.http.CacheControl.maxAge(7, java.util.concurrent.TimeUnit.DAYS).cachePublic())
                .resourceChain(true);
        // Lower order so RequestMapping (ForwardController) is tried first for "/"
        registry.setOrder(Ordered.LOWEST_PRECEDENCE);
    }
}
