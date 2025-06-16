package com.hackathon.hr.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.annotation.PostConstruct;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
    
    private static final Logger logger = LoggerFactory.getLogger(WebMvcConfig.class);
    
    // Read from environment variable or use default from application.properties
    @Value("${CORS_ALLOWED_ORIGINS:${cors.allowed.origins:https://demos.somdip.dev,https://somdip.dev,https://www.somdip.dev,http://localhost:8080,http://localhost:8081}}")
    private String allowedOrigins;
    
    // Use default if not provided
    @Value("${cors.allowed.methods:GET,POST,PUT,DELETE,OPTIONS,HEAD}")
    private String allowedMethods;
    
    @Value("${cors.max.age:3600}")
    private long corsMaxAge;
    
    @PostConstruct
    public void logConfiguration() {
        logger.info("CORS Configuration initialized:");
        logger.info("  Allowed Origins: {}", allowedOrigins);
        logger.info("  Allowed Methods: {}", allowedMethods);
        logger.info("  Max Age: {}", corsMaxAge);
    }
    
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String[] origins = allowedOrigins.split(",");
        String[] methods = allowedMethods.split(",");
        
        registry.addMapping("/**")
            .allowedOrigins(origins)
            .allowedMethods(methods)
            .allowedHeaders("*")
            .exposedHeaders(
                "X-Frame-Options",
                "Content-Security-Policy",
                "X-Content-Type-Options",
                "X-XSS-Protection"
            )
            .allowCredentials(true)
            .maxAge(corsMaxAge);
            
        // Specific configuration for actuator endpoints
        registry.addMapping("/actuator/**")
            .allowedOrigins(origins)
            .allowedMethods("GET", "OPTIONS")
            .allowedHeaders("*")
            .allowCredentials(true)
            .maxAge(corsMaxAge);
            
        logger.info("CORS configured with {} allowed origins", origins.length);
    }
    
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Static resources configuration
        registry.addResourceHandler("/static/**")
            .addResourceLocations("classpath:/static/")
            .setCachePeriod(3600);
            
        registry.addResourceHandler("/css/**")
            .addResourceLocations("classpath:/static/css/")
            .setCachePeriod(3600);
            
        registry.addResourceHandler("/js/**")
            .addResourceLocations("classpath:/static/js/")
            .setCachePeriod(3600);
            
        registry.addResourceHandler("/images/**")
            .addResourceLocations("classpath:/static/images/")
            .setCachePeriod(3600);
    }
}