package com.app.billing.config;

import com.app.billing.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.http.HttpMethod;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final UserDetailsService userDetailsService;
    
    @Value("${cors.allowed-origins}")
    private String allowedOrigins;
    
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll() // Allow all OPTIONS preflight requests - MUST be first
                // Public endpoints
                .requestMatchers("/", "/health", "/auth/**", "/api/auth/**", "/log-client-error", "/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html").permitAll()
                // Static files for Angular frontend (local deployment)
                .requestMatchers("/index.html", "/*.js", "/*.css", "/*.ico", "/*.png", "/*.svg", "/*.jpg", "/*.jpeg", "/*.gif").permitAll()
                .requestMatchers("/*.woff", "/*.woff2", "/*.ttf", "/*.eot", "/*.map").permitAll()
                .requestMatchers("/assets/**", "/*.json").permitAll()
                .requestMatchers("/images/**").permitAll()
                // Angular SPA routes (local deployment) - these return index.html
                .requestMatchers("/login", "/dashboard", "/dashboard/**", "/billing/**", "/master/**").permitAll()
                .requestMatchers("/reports/**", "/settings/**", "/employees/**", "/purchase-expense/**", "/accounting", "/accounting/**").permitAll()
                .requestMatchers("/print-book/**", "/inventory/**", "/profile/**").permitAll()
                // Admin-only management APIs. Fine-grained, per-action authorization for all
                // other business APIs is enforced via @RequiresPermission (see PermissionAspect),
                // so here we only require an authenticated session and lock down the admin surfaces.
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .requestMatchers("/employee-categories/**").hasRole("ADMIN")
                .requestMatchers("/entitlements/**").hasRole("ADMIN")
                .requestMatchers("/module-registry/**").hasRole("ADMIN")
                .requestMatchers("/audit-logs/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .authenticationProvider(authenticationProvider())
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        
        return http.build();
    }
    
    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }
    
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
    
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
    
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // Read allowed origins from application properties (comma-separated)
        List<String> origins = Arrays.asList(allowedOrigins.split(","));
        // Trim whitespace and normalize URLs (remove double slashes, trailing slashes before port)
        origins = origins.stream()
                .map(String::trim)
                .map(origin -> origin.replaceAll(":/(\\d)", ":$1")) // Fix :/port to :port
                .map(origin -> origin.replaceAll("//+", "/")) // Remove double slashes
                .map(origin -> origin.replaceAll("http:/", "http://")) // Fix http:/ to http://
                .map(origin -> origin.replaceAll("https:/", "https://")) // Fix https:/ to https://
                .toList();

        System.out.println("CORS Allowed Origins: " + origins);
        configuration.setAllowedOrigins(origins);
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L); // Cache preflight response for 1 hour
        // Expose Content-Disposition header for PDF downloads
        configuration.setExposedHeaders(Arrays.asList("Content-Disposition", "Content-Type", "Content-Length"));
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}

