package com.app.billing.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;
import java.util.List;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    
    @Value("${cors.allowed-origins}")
    private String allowedOrigins;
    
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // Parse allowed origins from properties
        List<String> origins = Arrays.asList(allowedOrigins.split(","));
        // Normalize URLs
        origins = origins.stream()
                .map(String::trim)
                .map(origin -> origin.replaceAll(":/(\\d)", ":$1")) // Fix :/port to :port
                .map(origin -> origin.replaceAll("//+", "/")) // Remove double slashes
                .map(origin -> origin.replaceAll("http:/", "http://")) // Fix http:/ to http://
                .map(origin -> origin.replaceAll("https:/", "https://")) // Fix https:/ to https://
                .toList();
        
        System.out.println("WebMvcConfigurer CORS Allowed Origins: " + origins);
        
        registry.addMapping("/**")
                .allowedOrigins(origins.toArray(new String[origins.size()]))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/images/**")
                .addResourceLocations("classpath:/images/");
    }
}

