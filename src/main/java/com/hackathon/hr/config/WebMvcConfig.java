package com.hackathon.hr.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
    
    @Value("${cors.allowed.origins}")
    private String allowedOrigins;
    
    @Value("${cors.allowed.methods}")
    private String allowedMethods;
    
    @Value("${cors.max.age:3600}")
    private long corsMaxAge;
    
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