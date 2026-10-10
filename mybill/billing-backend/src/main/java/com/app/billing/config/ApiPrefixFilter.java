package com.app.billing.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Filter that rewrites /api/* requests to /* for controller routing.
 * 
 * This allows the application to run without a context-path while still
 * accepting requests at /api/* paths. This enables:
 * - Frontend served at root (/)
 * - API served at /api/*
 * - Both on the same port
 * 
 * Only active in 'local' profile where we serve both frontend and API.
 */
@Component
@Profile("local")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApiPrefixFilter implements Filter {
    
    private static final String API_PREFIX = "/api";
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String requestURI = httpRequest.getRequestURI();
        
        // If request starts with /api, strip the prefix for internal routing
        if (requestURI.startsWith(API_PREFIX)) {
            String path = requestURI.substring(API_PREFIX.length());
            final String newURI = path.isEmpty() ? "/" : path;
            
            // Wrap request with modified URI
            HttpServletRequest wrappedRequest = new HttpServletRequestWrapper(httpRequest) {
                @Override
                public String getRequestURI() {
                    return newURI;
                }
                
                @Override
                public String getServletPath() {
                    return newURI;
                }
            };
            
            chain.doFilter(wrappedRequest, response);
        } else {
            // Non-API requests pass through normally (static files, etc.)
            chain.doFilter(request, response);
        }
    }
}
