package com.hackathon.hr.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import javax.servlet.*;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.annotation.PostConstruct;

@Configuration
public class HeaderConfig {
    
    private static final Logger logger = LoggerFactory.getLogger(HeaderConfig.class);
    
    // Read from environment variable first, then fall back to property
    @Value("${SECURITY_HEADERS_CONTENT_SECURITY_POLICY:${security.headers.content.security.policy:frame-ancestors 'self' https://somdip.dev https://www.somdip.dev http://localhost:8080 http://localhost:8081;}}")
    private String contentSecurityPolicy;
    
    @Value("${iframe.embedding.enabled:true}")
    private boolean iframeEmbeddingEnabled;
    
    @PostConstruct
    public void logConfiguration() {
        logger.info("Security Headers Configuration:");
        logger.info("  Content-Security-Policy: {}", contentSecurityPolicy);
        logger.info("  iFrame embedding enabled: {}", iframeEmbeddingEnabled);
    }
    
    @Bean
    public FilterRegistrationBean<HeaderFilter> headerFilterRegistration() {
        FilterRegistrationBean<HeaderFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new HeaderFilter(contentSecurityPolicy, iframeEmbeddingEnabled));
        registration.addUrlPatterns("/*");
        registration.setName("headerFilter");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
    
    public static class HeaderFilter implements Filter {
        
        private static final Logger logger = LoggerFactory.getLogger(HeaderFilter.class);
        
        private final String contentSecurityPolicy;
        private final boolean iframeEmbeddingEnabled;
        
        public HeaderFilter(String contentSecurityPolicy, boolean iframeEmbeddingEnabled) {
            this.contentSecurityPolicy = contentSecurityPolicy;
            this.iframeEmbeddingEnabled = iframeEmbeddingEnabled;
        }
        
        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) 
                throws IOException, ServletException {
            
            HttpServletResponse httpResponse = (HttpServletResponse) response;
            
            // Allow iframe embedding if enabled
            if (iframeEmbeddingEnabled) {
                // Remove X-Frame-Options to allow embedding
                httpResponse.setHeader("X-Frame-Options", "ALLOWALL");
                
                // Set Content-Security-Policy
                if (contentSecurityPolicy != null && !contentSecurityPolicy.isEmpty()) {
                    httpResponse.setHeader("Content-Security-Policy", contentSecurityPolicy);
                }
            } else {
                // Deny iframe embedding
                httpResponse.setHeader("X-Frame-Options", "DENY");
            }
            
            // Other security headers
            httpResponse.setHeader("X-Content-Type-Options", "nosniff");
            httpResponse.setHeader("X-XSS-Protection", "1; mode=block");
            httpResponse.setHeader("Referrer-Policy", "same-origin");
            
            // Continue with the filter chain
            chain.doFilter(request, response);
        }
        
        @Override
        public void init(FilterConfig filterConfig) throws ServletException {
            logger.debug("HeaderFilter initialized");
        }
        
        @Override
        public void destroy() {
            logger.debug("HeaderFilter destroyed");
        }
    }
}