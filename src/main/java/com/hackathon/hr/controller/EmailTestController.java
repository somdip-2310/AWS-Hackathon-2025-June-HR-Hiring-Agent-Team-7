package com.hackathon.hr.controller;

import com.hackathon.hr.service.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/test")
@Profile({"dev", "test"}) // Only available in dev/test profiles
public class EmailTestController {
    
    private static final Logger logger = LoggerFactory.getLogger(EmailTestController.class);
    
    @Autowired
    private EmailService emailService;
    
    @Value("${sendgrid.enabled:false}")
    private boolean sendgridEnabled;
    
    @PostMapping("/send-test-email")
    public ResponseEntity<Map<String, Object>> sendTestEmail(@RequestParam String email) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            logger.info("Test email requested for: {}", email);
            
            // Send verification email
            CompletableFuture<Boolean> result = emailService.sendVerificationEmail(email, "TEST123");
            
            boolean sent = result.get(10, TimeUnit.SECONDS);
            
            response.put("success", sent);
            response.put("message", sent ? "Test email sent successfully" : "Failed to send test email");
            response.put("sendgridEnabled", sendgridEnabled);
            response.put("emailStats", emailService.getEmailStatistics());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error in test email controller", e);
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
    
    @GetMapping("/email-service-health")
    public ResponseEntity<Map<String, Object>> getEmailServiceHealth() {
        return ResponseEntity.ok(emailService.getServiceHealth());
    }
}