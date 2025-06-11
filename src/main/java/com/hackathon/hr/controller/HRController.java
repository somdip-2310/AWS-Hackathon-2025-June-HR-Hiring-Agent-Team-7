// src/main/java/com/hackathon/hr/controller/HRController.java
package com.hackathon.hr.controller;

import com.hackathon.hr.model.Candidate;
import com.hackathon.hr.model.JobRequirement;
import com.hackathon.hr.model.MatchResult;
import com.hackathon.hr.service.CandidateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class HRController {
    
    private static final Logger logger = LoggerFactory.getLogger(HRController.class);

    private final CandidateService candidateService;
    
    // Service configuration from properties
    @Value("${demo.info.title:HR Resume Screening AI Demo}")
    private String serviceTitle;
    
    @Value("${demo.info.version:1.0.0}")
    private String serviceVersion;
    
    @Value("${demo.info.description:Enterprise AI solution for automated resume screening}")
    private String serviceDescription;
    
    @Value("${hr.demo.processing.enabled:true}")
    private boolean processingEnabled;
    
    @Value("${hr.demo.ai.analysis.enabled:true}")
    private boolean aiAnalysisEnabled;
    
    // Service startup time for uptime calculation
    private final LocalDateTime startupTime = LocalDateTime.now();

    public HRController(CandidateService candidateService) {
        this.candidateService = candidateService;
    }

    // ========================================
    // MAIN APPLICATION ENDPOINTS
    // ========================================

    @GetMapping("/")
    public String index(Model model) {
        try {
            model.addAttribute("candidates", candidateService.getAllCandidates());
            model.addAttribute("jobs", candidateService.getAllJobs());
            model.addAttribute("serviceInfo", getServiceInfo());
            model.addAttribute("processingEnabled", processingEnabled);
            model.addAttribute("aiAnalysisEnabled", aiAnalysisEnabled);
            
            logger.debug("Index page loaded with {} candidates and {} jobs", 
                        candidateService.getAllCandidates().size(), 
                        candidateService.getAllJobs().size());
            
            return "index";
        } catch (Exception e) {
            logger.error("Error loading index page", e);
            model.addAttribute("error", "Service temporarily unavailable");
            return "error";
        }
    }

    // ========================================
    // RESUME PROCESSING ENDPOINTS
    // ========================================

    @PostMapping("/api/upload")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> uploadResume(@RequestParam("file") MultipartFile file) {
        Map<String, Object> response = new HashMap<>();

        try {
            // Check if processing is enabled
            if (!processingEnabled) {
                response.put("success", false);
                response.put("error", "Resume processing is currently disabled");
                return ResponseEntity.status(503).body(response);
            }

            // Validate file
            if (file.isEmpty()) {
                response.put("success", false);
                response.put("error", "Please select a file to upload");
                return ResponseEntity.badRequest().body(response);
            }

            String contentType = file.getContentType();
            if (!isValidFileType(contentType)) {
                response.put("success", false);
                response.put("error", "Only PDF and Word documents are supported");
                return ResponseEntity.badRequest().body(response);
            }

            // Check file size (additional validation)
            if (file.getSize() > 10 * 1024 * 1024) { // 10MB limit
                response.put("success", false);
                response.put("error", "File size exceeds 10MB limit");
                return ResponseEntity.badRequest().body(response);
            }

            // Process resume
            logger.info("Processing resume upload: {}", file.getOriginalFilename());
            Candidate candidate = candidateService.processResume(file);

            response.put("success", true);
            response.put("candidateId", candidate.getId());
            response.put("fileName", candidate.getFileName());
            response.put("message", "Resume processed successfully");
            response.put("timestamp", LocalDateTime.now().toString());

            logger.info("Resume uploaded successfully: {} (ID: {})", 
                       file.getOriginalFilename(), candidate.getId());
            
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error uploading resume: {}", file.getOriginalFilename(), e);
            response.put("success", false);
            response.put("error", "Failed to process resume: " + e.getMessage());
            response.put("timestamp", LocalDateTime.now().toString());
            return ResponseEntity.status(500).body(response);
        }
    }

    @PostMapping("/api/match")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> matchCandidates(@RequestParam("jobId") String jobId) {
        Map<String, Object> response = new HashMap<>();

        try {
            // Check if AI analysis is enabled
            if (!aiAnalysisEnabled) {
                response.put("success", false);
                response.put("error", "AI analysis is currently disabled");
                return ResponseEntity.status(503).body(response);
            }

            logger.info("Matching candidates for job ID: {}", jobId);
            List<MatchResult> matches = candidateService.matchCandidates(jobId);

            response.put("success", true);
            response.put("matches", matches);
            response.put("totalCandidates", candidateService.getAllCandidates().size());
            response.put("matchCount", matches.size());
            response.put("jobId", jobId);
            response.put("timestamp", LocalDateTime.now().toString());

            logger.info("Found {} matches for job ID: {}", matches.size(), jobId);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error matching candidates for job ID: {}", jobId, e);
            response.put("success", false);
            response.put("error", "Failed to match candidates: " + e.getMessage());
            response.put("timestamp", LocalDateTime.now().toString());
            return ResponseEntity.status(500).body(response);
        }
    }

    // ========================================
    // DATA ACCESS ENDPOINTS
    // ========================================

    @GetMapping("/api/candidates")
    @ResponseBody
    public ResponseEntity<List<Candidate>> getCandidates() {
        try {
            List<Candidate> candidates = candidateService.getAllCandidates();
            logger.debug("Retrieved {} candidates", candidates.size());
            return ResponseEntity.ok(candidates);
        } catch (Exception e) {
            logger.error("Error retrieving candidates", e);
            return ResponseEntity.status(500).build();
        }
    }

    @GetMapping("/api/jobs")
    @ResponseBody
    public ResponseEntity<List<JobRequirement>> getJobs() {
        try {
            List<JobRequirement> jobs = candidateService.getAllJobs();
            logger.debug("Retrieved {} job requirements", jobs.size());
            return ResponseEntity.ok(jobs);
        } catch (Exception e) {
            logger.error("Error retrieving job requirements", e);
            return ResponseEntity.status(500).build();
        }
    }

    // ========================================
    // SERVICE INFO & HEALTH ENDPOINTS
    // ========================================

    @GetMapping("/api/service/info")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getServiceInfo() {
        Map<String, Object> serviceInfo = new HashMap<>();
        
        try {
            serviceInfo.put("serviceName", "HR Demo Service");
            serviceInfo.put("title", serviceTitle);
            serviceInfo.put("version", serviceVersion);
            serviceInfo.put("description", serviceDescription);
            serviceInfo.put("status", "running");
            serviceInfo.put("startupTime", startupTime.toString());
            serviceInfo.put("uptime", calculateUptime());
            serviceInfo.put("timestamp", LocalDateTime.now().toString());
            
            // Feature flags
            Map<String, Boolean> features = new HashMap<>();
            features.put("resumeProcessing", processingEnabled);
            features.put("aiAnalysis", aiAnalysisEnabled);
            features.put("candidateMatching", true);
            serviceInfo.put("features", features);
            
            // Statistics
            Map<String, Object> stats = new HashMap<>();
            stats.put("totalCandidates", candidateService.getAllCandidates().size());
            stats.put("totalJobs", candidateService.getAllJobs().size());
            serviceInfo.put("statistics", stats);
            
            // Technology stack
            serviceInfo.put("techStack", List.of("Spring Boot", "AWS S3", "Amazon Textract", "Amazon Bedrock"));
            
            return ResponseEntity.ok(serviceInfo);
            
        } catch (Exception e) {
            logger.error("Error getting service info", e);
            serviceInfo.put("status", "error");
            serviceInfo.put("error", e.getMessage());
            return ResponseEntity.status(500).body(serviceInfo);
        }
    }

    @GetMapping("/api/service/status")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getServiceStatus() {
        Map<String, Object> status = new HashMap<>();
        
        try {
            // Basic service health
            status.put("status", "UP");
            status.put("timestamp", LocalDateTime.now().toString());
            status.put("uptime", calculateUptime());
            
            // Service capabilities
            Map<String, String> capabilities = new HashMap<>();
            capabilities.put("resumeProcessing", processingEnabled ? "enabled" : "disabled");
            capabilities.put("aiAnalysis", aiAnalysisEnabled ? "enabled" : "disabled");
            capabilities.put("candidateMatching", "enabled");
            status.put("capabilities", capabilities);
            
            // Quick stats
            status.put("candidateCount", candidateService.getAllCandidates().size());
            status.put("jobCount", candidateService.getAllJobs().size());
            
            return ResponseEntity.ok(status);
            
        } catch (Exception e) {
            logger.error("Error getting service status", e);
            status.put("status", "DOWN");
            status.put("error", e.getMessage());
            status.put("timestamp", LocalDateTime.now().toString());
            return ResponseEntity.status(500).body(status);
        }
    }

    @GetMapping("/api/service/health")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getServiceHealth() {
        Map<String, Object> health = new HashMap<>();
        
        try {
            // Check service dependencies
            boolean isHealthy = checkServiceHealth();
            
            health.put("status", isHealthy ? "UP" : "DOWN");
            health.put("timestamp", LocalDateTime.now().toString());
            
            // Component health checks
            Map<String, String> components = new HashMap<>();
            components.put("candidateService", candidateService != null ? "UP" : "DOWN");
            components.put("database", "UP"); // Add actual DB check if using database
            components.put("aws", "UP");      // Add actual AWS health check
            health.put("components", components);
            
            return ResponseEntity.ok(health);
            
        } catch (Exception e) {
            logger.error("Error checking service health", e);
            health.put("status", "DOWN");
            health.put("error", e.getMessage());
            return ResponseEntity.status(500).body(health);
        }
    }

    // ========================================
    // UTILITY METHODS
    // ========================================

    private boolean isValidFileType(String contentType) {
        return contentType != null && (
                contentType.equals("application/pdf") ||
                contentType.equals("application/msword") ||
                contentType.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document") ||
                contentType.equals("text/plain")
        );
    }

    private boolean checkServiceHealth() {
        try {
            // Basic health checks
            if (candidateService == null) {
                return false;
            }
            
            // Test service functionality
            candidateService.getAllCandidates();
            candidateService.getAllJobs();
            
            return true;
        } catch (Exception e) {
            logger.error("Service health check failed", e);
            return false;
        }
    }

    private String calculateUptime() {
        try {
            LocalDateTime now = LocalDateTime.now();
            long seconds = java.time.Duration.between(startupTime, now).getSeconds();
            
            long hours = seconds / 3600;
            long minutes = (seconds % 3600) / 60;
            long secs = seconds % 60;
            
            return String.format("%02d:%02d:%02d", hours, minutes, secs);
        } catch (Exception e) {
            return "unknown";
        }
    }

    // ========================================
    // ERROR HANDLING
    // ========================================

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleException(Exception e) {
        logger.error("Unhandled exception in HR Controller", e);
        
        Map<String, Object> error = new HashMap<>();
        error.put("success", false);
        error.put("error", "Internal server error");
        error.put("timestamp", LocalDateTime.now().toString());
        
        return ResponseEntity.status(500).body(error);
    }
}