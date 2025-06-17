package com.hackathon.hr.service;

import com.sendgrid.*;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Email;
import com.sendgrid.helpers.mail.objects.Personalization;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Service
public class EmailService {
    
    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);
    
    private final SendGrid sendGrid;
    private final TemplateEngine templateEngine;
    
    @Value("${sendgrid.from.email}")
    private String fromEmail;
    
    @Value("${sendgrid.from.name}")
    private String fromName;
    
    @Value("${sendgrid.enabled:false}")
    private boolean sendgridEnabled;
    
    @Value("${email.max.retry.attempts:3}")
    private int maxRetryAttempts;
    
    @Value("${email.retry.delay.seconds:5}")
    private int retryDelaySeconds;
    
    @Value("${hr.demo.email.log.codes:true}")
    private boolean logCodes;
    
    @Value("${application.url:https://demos.somdip.dev/hr-agent}")
    private String applicationUrl;
    
    // Service startup time for uptime calculation
    private final LocalDateTime startupTime = LocalDateTime.now();
    
    // Track email statistics
    private final EmailStats stats = new EmailStats();
    
    // Track rate limiting
    private final Map<String, RateLimitInfo> rateLimitMap = new ConcurrentHashMap<>();
    
    public EmailService(@Value("${sendgrid.api.key:}") String apiKey, 
                       TemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
        
        // Only initialize SendGrid if API key is provided
        if (apiKey != null && !apiKey.trim().isEmpty() && !apiKey.equals("your-api-key-here")) {
            this.sendGrid = new SendGrid(apiKey);
            logger.info("SendGrid email service initialized successfully");
        } else {
            this.sendGrid = null;
            logger.warn("SendGrid API key not configured - email sending will be simulated");
        }
    }
    
    public CompletableFuture<Boolean> sendVerificationEmail(String toEmail, String verificationCode) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Check rate limiting
                if (isRateLimited(toEmail)) {
                    logger.warn("Rate limit exceeded for email: {}", maskEmail(toEmail));
                    return false;
                }
                
                // Log verification code if enabled
                if (logCodes) {
                    logger.info("=== VERIFICATION CODE ===");
                    logger.info("Email: {}", maskEmail(toEmail));
                    logger.info("Code: {}", verificationCode);
                    logger.info("SendGrid Enabled: {}", sendgridEnabled);
                    logger.info("From Email: {}", fromEmail);
                    logger.info("========================");
                }
                
                // If SendGrid is not enabled or not configured, simulate success
                if (!sendgridEnabled || sendGrid == null) {
                    logger.info("Email sending simulated for: {} (SendGrid disabled)", maskEmail(toEmail));
                    stats.recordSuccess();
                    updateRateLimit(toEmail);
                    return true;
                }
                
                // Create email context
                Context context = new Context();
                context.setVariable("verificationCode", verificationCode);
                context.setVariable("userEmail", toEmail);
                context.setVariable("timestamp", LocalDateTime.now());
                
                // Generate HTML content from template
                String htmlContent = templateEngine.process("email/verification", context);
                
                // Create email
                Email from = new Email(fromEmail, fromName);
                Email to = new Email(toEmail);
                String subject = "HR Agent - Your Verification Code: " + verificationCode;
                Content content = new Content("text/html", htmlContent);
                
                Mail mail = new Mail(from, subject, to, content);
                
                // Send email with retry logic
                boolean sent = sendEmailWithRetry(mail);
                
                if (sent) {
                    stats.recordSuccess();
                    updateRateLimit(toEmail);
                } else {
                    stats.recordFailure();
                }
                
                return sent;
                
            } catch (Exception e) {
                logger.error("Failed to send verification email to: {}", maskEmail(toEmail), e);
                stats.recordFailure();
                return false;
            }
        });
    }
    
    public CompletableFuture<Boolean> sendSessionExpiredEmail(String toEmail) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // If SendGrid is not enabled, skip
                if (!sendgridEnabled || sendGrid == null) {
                    logger.info("Session expired email simulated for: {} (SendGrid disabled)", maskEmail(toEmail));
                    return true;
                }
                
                Context context = new Context();
                context.setVariable("userEmail", toEmail);
                context.setVariable("timestamp", LocalDateTime.now());
                context.setVariable("applicationUrl", applicationUrl);
                
                String htmlContent = templateEngine.process("email/session-expired", context);
                
                Email from = new Email(fromEmail, fromName);
                Email to = new Email(toEmail);
                String subject = "HR Agent - Session Expired";
                Content content = new Content("text/html", htmlContent);
                
                Mail mail = new Mail(from, subject, to, content);
                
                boolean sent = sendEmailWithRetry(mail);
                
                if (sent) {
                    stats.recordSuccess();
                } else {
                    stats.recordFailure();
                }
                
                return sent;
                
            } catch (Exception e) {
                logger.error("Failed to send session expired email to: {}", maskEmail(toEmail), e);
                stats.recordFailure();
                return false;
            }
        });
    }
    
    public CompletableFuture<Boolean> sendWelcomeEmail(String toEmail) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // If SendGrid is not enabled, skip
                if (!sendgridEnabled || sendGrid == null) {
                    logger.info("Welcome email simulated for: {} (SendGrid disabled)", maskEmail(toEmail));
                    return true;
                }
                
                Context context = new Context();
                context.setVariable("userEmail", toEmail);
                context.setVariable("timestamp", LocalDateTime.now());
                
                String htmlContent = templateEngine.process("email/welcome", context);
                
                Email from = new Email(fromEmail, fromName);
                Email to = new Email(toEmail);
                String subject = "Welcome to HR Agent";
                Content content = new Content("text/html", htmlContent);
                
                Mail mail = new Mail(from, subject, to, content);
                
                boolean sent = sendEmailWithRetry(mail);
                
                if (sent) {
                    stats.recordSuccess();
                } else {
                    stats.recordFailure();
                }
                
                return sent;
                
            } catch (Exception e) {
                logger.error("Failed to send welcome email to: {}", maskEmail(toEmail), e);
                stats.recordFailure();
                return false;
            }
        });
    }
    
    private boolean sendEmailWithRetry(Mail mail) {
        if (!sendgridEnabled || sendGrid == null) {
            logger.info("SendGrid is disabled or not configured. Email would have been sent to: {}", 
                       mail.getPersonalization().get(0).getTos().get(0).getEmail());
            return true;
        }
        
        int attempts = 0;
        while (attempts < maxRetryAttempts) {
            try {
                Request request = new Request();
                request.setMethod(Method.POST);
                request.setEndpoint("mail/send");
                request.setBody(mail.build());
                
                Response response = sendGrid.api(request);
                
                if (response.getStatusCode() >= 200 && response.getStatusCode() < 300) {
                    logger.info("Email sent successfully to: {} (Status: {})", 
                               maskEmail(mail.getPersonalization().get(0).getTos().get(0).getEmail()),
                               response.getStatusCode());
                    return true;
                } else {
                    logger.warn("Failed to send email. Status: {}, Body: {}", 
                               response.getStatusCode(), response.getBody());
                }
                
            } catch (IOException e) {
                logger.error("Error sending email (attempt {}/{})", attempts + 1, maxRetryAttempts, e);
            }
            
            attempts++;
            if (attempts < maxRetryAttempts) {
                try {
                    TimeUnit.SECONDS.sleep(retryDelaySeconds);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        
        return false;
    }
    
    private boolean isRateLimited(String email) {
        RateLimitInfo info = rateLimitMap.get(email.toLowerCase());
        if (info == null) {
            return false;
        }
        
        // Check if rate limit window has expired
        if (Duration.between(info.firstAttempt, LocalDateTime.now()).toMinutes() > 15) {
            rateLimitMap.remove(email.toLowerCase());
            return false;
        }
        
        return info.attemptCount >= 5;
    }
    
    private void updateRateLimit(String email) {
        String key = email.toLowerCase();
        RateLimitInfo info = rateLimitMap.computeIfAbsent(key, k -> new RateLimitInfo());
        info.attemptCount++;
    }
    
    private String maskEmail(String email) {
        if (email == null || email.length() < 3) return email;
        int atIndex = email.indexOf('@');
        if (atIndex <= 0) return email;
        
        String username = email.substring(0, atIndex);
        String domain = email.substring(atIndex);
        
        if (username.length() <= 2) {
            return username.charAt(0) + "*" + domain;
        } else {
            return username.charAt(0) + "*".repeat(username.length() - 2) + 
                   username.charAt(username.length() - 1) + domain;
        }
    }
    
    // Get email statistics
    public EmailStats getEmailStatistics() {
        return stats;
    }
    
    // Get service health
    public Map<String, Object> getServiceHealth() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", sendGrid != null ? "UP" : "SIMULATED");
        health.put("sendgridEnabled", sendgridEnabled);
        health.put("fromEmail", fromEmail);
        health.put("fromName", fromName);
        health.put("totalSent", stats.getTotalSent());
        health.put("totalFailed", stats.getTotalFailed());
        health.put("lastSentTime", stats.getLastSentTime());
        health.put("uptime", Duration.between(startupTime, LocalDateTime.now()).toString());
        return health;
    }
    
    // Email statistics tracking
    public static class EmailStats {
        private int totalSent = 0;
        private int totalFailed = 0;
        private LocalDateTime lastSentTime;
        
        public synchronized void recordSuccess() {
            totalSent++;
            lastSentTime = LocalDateTime.now();
        }
        
        public synchronized void recordFailure() {
            totalFailed++;
        }
        
        // Getters
        public int getTotalSent() { return totalSent; }
        public int getTotalFailed() { return totalFailed; }
        public LocalDateTime getLastSentTime() { return lastSentTime; }
    }
    
    // Rate limit tracking
    private static class RateLimitInfo {
        private int attemptCount = 0;
        private final LocalDateTime firstAttempt = LocalDateTime.now();
    }
}