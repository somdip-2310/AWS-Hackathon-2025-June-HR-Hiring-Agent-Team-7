// FIXED HRController.java - Remove duplicate imports and endpoints
package com.hackathon.hr.controller;

import com.hackathon.hr.model.Candidate;
import com.hackathon.hr.model.JobRequirement;
import com.hackathon.hr.model.MatchResult;
import com.hackathon.hr.service.CandidateService;
import com.hackathon.hr.service.SessionManagementService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Controller
public class HRController {
    
    private static final Logger logger = LoggerFactory.getLogger(HRController.class);

    private final CandidateService candidateService;
    private final SessionManagementService sessionManagementService;
    
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
    
    // Track upload processing status (in-memory for demo purposes)
    private final Map<String, ProcessingStatus> processingStatusMap = new ConcurrentHashMap<>();

    public HRController(CandidateService candidateService, SessionManagementService sessionManagementService) {
        this.candidateService = candidateService;
        this.sessionManagementService = sessionManagementService;
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
    // SESSION MANAGEMENT ENDPOINTS (FIXED - NO DUPLICATES)
    // ========================================

    @GetMapping("/api/session/status")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getSessionStatus() {
        try {
            // First check for cleanup
            SessionManagementService.SessionCleanupResult cleanupResult = 
                sessionManagementService.cleanupExpiredSessions();
            
            // Get current session info
            Map<String, Object> response = sessionManagementService.getCurrentSessionInfo();
            
            // Add cleanup information
            response.put("sessionExpired", cleanupResult.isSessionExpired());
            response.put("dataCleanupRequired", cleanupResult.isSessionExpired());
            
            // If session expired, clear data
            if (cleanupResult.isSessionExpired()) {
                int deletedCount = candidateService.clearAllCandidates();
                response.put("candidatesCleared", deletedCount);
            }
            
            response.put("timestamp", LocalDateTime.now().toString());
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error getting session status", e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Failed to get session status");
            return ResponseEntity.status(500).body(error);
        }
    }

    @GetMapping("/api/session/cleanup-check")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> checkAndCleanupSessions() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            // Check for expired sessions and get cleanup result
            SessionManagementService.SessionCleanupResult cleanupResult = 
                sessionManagementService.cleanupExpiredSessions();
            
            response.put("sessionExpired", cleanupResult.isSessionExpired());
            response.put("dataCleanupRequired", cleanupResult.isSessionExpired());
            
            // If session expired, also clear all candidate data
            if (cleanupResult.isSessionExpired()) {
                int deletedCount = candidateService.clearAllCandidates();
                response.put("candidatesCleared", deletedCount);
                response.put("expiredUserEmail", 
                    maskEmailForLogging(cleanupResult.getExpiredUserEmail()));
                
                logger.info("Session expired - cleared {} candidates for user: {}", 
                           deletedCount, 
                           maskEmailForLogging(cleanupResult.getExpiredUserEmail()));
            }
            
            response.put("timestamp", LocalDateTime.now().toString());
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error during cleanup check", e);
            response.put("error", "Failed to check cleanup status");
            return ResponseEntity.status(500).body(response);
        }
    }

    @PostMapping("/api/session/request-verification")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> requestEmailVerification(@RequestParam("email") String email) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            SessionManagementService.EmailVerificationResult result = 
                sessionManagementService.sendVerificationCode(email);
            
            response.put("success", result.isSuccess());
            response.put("message", result.getMessage());
            response.put("timestamp", LocalDateTime.now().toString());
            
            if (result.isSuccess()) {
                logger.info("Verification code requested for email: {}", maskEmailForLogging(email));
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.badRequest().body(response);
            }
            
        } catch (Exception e) {
            logger.error("Error requesting verification for email: {}", maskEmailForLogging(email), e);
            response.put("success", false);
            response.put("message", "Failed to send verification code");
            return ResponseEntity.status(500).body(response);
        }
    }

    @PostMapping("/api/session/verify-email")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> verifyEmail(
            @RequestParam("email") String email, 
            @RequestParam("code") String code) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            SessionManagementService.EmailVerificationResult verifyResult = 
                sessionManagementService.verifyEmail(email, code);
            
            if (!verifyResult.isSuccess()) {
                response.put("success", false);
                response.put("message", verifyResult.getMessage());
                return ResponseEntity.badRequest().body(response);
            }
            
            // Start session after successful verification
            SessionManagementService.SessionStartResult sessionResult = 
                sessionManagementService.startSession(email);
            
            response.put("success", sessionResult.isSuccess());
            response.put("message", sessionResult.getMessage());
            response.put("sessionId", sessionResult.getSessionId());
            response.put("sessionDuration", 7); // 7 minutes
            response.put("timestamp", LocalDateTime.now().toString());
            
            if (sessionResult.isSuccess()) {
                logger.info("Email verified and session started for: {}", maskEmailForLogging(email));
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.badRequest().body(response);
            }
            
        } catch (Exception e) {
            logger.error("Error verifying email: {}", maskEmailForLogging(email), e);
            response.put("success", false);
            response.put("message", "Failed to verify email");
            return ResponseEntity.status(500).body(response);
        }
    }

    @PostMapping("/api/session/end")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> endSession(@RequestParam("sessionId") String sessionId) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            boolean ended = sessionManagementService.endSession(sessionId);
            
            response.put("success", ended);
            response.put("message", ended ? "Session ended successfully" : "Session not found");
            response.put("timestamp", LocalDateTime.now().toString());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error ending session: {}", sessionId, e);
            response.put("success", false);
            response.put("message", "Failed to end session");
            return ResponseEntity.status(500).body(response);
        }
    }

    @PostMapping("/api/session/force-reset")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> forceResetDemo() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            // Clear all candidates
            int deletedCount = candidateService.clearAllCandidates();
            
            // End any active sessions
            sessionManagementService.cleanupExpiredSessions();
            
            response.put("success", true);
            response.put("candidatesCleared", deletedCount);
            response.put("message", "Demo reset successfully");
            response.put("timestamp", LocalDateTime.now().toString());
            
            logger.info("Demo force reset - cleared {} candidates", deletedCount);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error during force reset", e);
            response.put("success", false);
            response.put("error", "Failed to reset demo");
            return ResponseEntity.status(500).body(response);
        }
    }

    // ========================================
    // EMAIL TRACKING ENDPOINTS
    // ========================================

    @GetMapping("/api/admin/emails")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getEmailRecords() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            List<Map<String, Object>> emailRecords = sessionManagementService.getAllEmailRecords();
            Map<String, Object> statistics = sessionManagementService.getEmailStatistics();
            
            response.put("success", true);
            response.put("emailRecords", emailRecords);
            response.put("statistics", statistics);
            response.put("timestamp", LocalDateTime.now().toString());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error retrieving email records", e);
            response.put("success", false);
            response.put("error", "Failed to retrieve email records");
            return ResponseEntity.status(500).body(response);
        }
    }

    @GetMapping("/api/admin/email-stats")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getEmailStatistics() {
        try {
            Map<String, Object> statistics = sessionManagementService.getEmailStatistics();
            return ResponseEntity.ok(statistics);
        } catch (Exception e) {
            logger.error("Error retrieving email statistics", e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Failed to retrieve email statistics");
            return ResponseEntity.status(500).body(error);
        }
    }

    // ========================================
    // RESUME PROCESSING ENDPOINTS (WITH SESSION VALIDATION)
    // ========================================

    @PostMapping("/api/upload")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> uploadResume(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "sessionId", required = false) String sessionId) {
        Map<String, Object> response = new HashMap<>();
        String trackingId = UUID.randomUUID().toString();
        LocalDateTime uploadStartTime = LocalDateTime.now();

        try {
            // Check if processing is enabled
            if (!processingEnabled) {
                response.put("success", false);
                response.put("error", "Resume processing is currently disabled");
                return ResponseEntity.status(503).body(response);
            }
            
            // Validate session for upload
            if (!sessionManagementService.validateSessionForUpload(sessionId)) {
                response.put("success", false);
                response.put("error", "Invalid or expired session. Please start a new session.");
                response.put("requiresNewSession", true);
                return ResponseEntity.status(401).body(response);
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
                response.put("error", "Only PDF is supported");
                return ResponseEntity.badRequest().body(response);
            }

            // Check file size (additional validation)
            if (file.getSize() > 10 * 1024 * 1024) { // 10MB limit
                response.put("success", false);
                response.put("error", "File size exceeds 10MB limit");
                return ResponseEntity.badRequest().body(response);
            }

            // Initialize processing status
            ProcessingStatus status = new ProcessingStatus(trackingId, file.getOriginalFilename());
            processingStatusMap.put(trackingId, status);

            // Process resume
            logger.info("Processing resume upload: {} for session: {}", file.getOriginalFilename(), sessionId);
            status.setStatus("extracting");
            status.setProgress(30);
            
            Candidate candidate = candidateService.processResume(file);
            
            status.setStatus("analyzing");
            status.setProgress(70);
            
            // Update status to completed
            status.setStatus("completed");
            status.setProgress(100);
            status.setCandidateId(candidate.getId());

            // Calculate processing time
            Duration processingDuration = Duration.between(uploadStartTime, LocalDateTime.now());
            
            response.put("success", true);
            response.put("candidateId", candidate.getId());
            response.put("fileName", candidate.getFileName());
            response.put("trackingId", trackingId);
            response.put("sessionId", sessionId);
            response.put("message", "Resume processed successfully");
            response.put("processingTime", processingDuration.toMillis() + "ms");
            response.put("timestamp", LocalDateTime.now().toString());
            
            // Include extracted data preview
            Map<String, Object> extractedData = new HashMap<>();
            extractedData.put("skillsCount", candidate.getTechnicalSkills() != null ? candidate.getTechnicalSkills().size() : 0);
            extractedData.put("experienceLevel", candidate.getExperienceLevel());
            response.put("extractedData", extractedData);

            logger.info("Resume uploaded successfully: {} (ID: {}) in {}ms for session: {}", 
                       file.getOriginalFilename(), candidate.getId(), processingDuration.toMillis(), sessionId);
            
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error uploading resume: {} for session: {}", file.getOriginalFilename(), sessionId, e);
            
            // Update status to failed
            if (processingStatusMap.containsKey(trackingId)) {
                ProcessingStatus status = processingStatusMap.get(trackingId);
                status.setStatus("failed");
                status.setError(e.getMessage());
            }
            
            response.put("success", false);
            response.put("error", "Failed to process resume: " + e.getMessage());
            response.put("trackingId", trackingId);
            response.put("timestamp", LocalDateTime.now().toString());
            return ResponseEntity.status(500).body(response);
        }
    }

    @PostMapping("/api/upload/batch")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> uploadMultipleResumes(
            @RequestParam("files") MultipartFile[] files,
            @RequestParam(value = "sessionId", required = false) String sessionId) {
        Map<String, Object> response = new HashMap<>();
        
        // Validate session for upload
        if (!sessionManagementService.validateSessionForUpload(sessionId)) {
            response.put("success", false);
            response.put("error", "Invalid or expired session. Please start a new session.");
            response.put("requiresNewSession", true);
            return ResponseEntity.status(401).body(response);
        }
        
        List<Map<String, Object>> results = new ArrayList<>();
        int successCount = 0;
        LocalDateTime batchStartTime = LocalDateTime.now();
        
        logger.info("Processing batch upload of {} files for session: {}", files.length, sessionId);
        
        for (MultipartFile file : files) {
            Map<String, Object> fileResult = new HashMap<>();
            fileResult.put("fileName", file.getOriginalFilename());
            
            try {
                if (!isValidFileType(file.getContentType())) {
                    fileResult.put("success", false);
                    fileResult.put("error", "Invalid file type");
                } else if (file.getSize() > 10 * 1024 * 1024) {
                    fileResult.put("success", false);
                    fileResult.put("error", "File size exceeds 10MB limit");
                } else {
                    Candidate candidate = candidateService.processResume(file);
                    fileResult.put("success", true);
                    fileResult.put("candidateId", candidate.getId());
                    fileResult.put("skillsExtracted", candidate.getTechnicalSkills() != null ? candidate.getTechnicalSkills().size() : 0);
                    successCount++;
                }
            } catch (Exception e) {
                fileResult.put("success", false);
                fileResult.put("error", e.getMessage());
                logger.error("Error processing file in batch: {} for session: {}", file.getOriginalFilename(), sessionId, e);
            }
            
            results.add(fileResult);
        }
        
        Duration batchDuration = Duration.between(batchStartTime, LocalDateTime.now());
        
        response.put("results", results);
        response.put("totalFiles", files.length);
        response.put("successCount", successCount);
        response.put("failureCount", files.length - successCount);
        response.put("sessionId", sessionId);
        response.put("processingTime", batchDuration.toMillis() + "ms");
        response.put("timestamp", LocalDateTime.now().toString());
        
        logger.info("Batch upload completed for session {}: {}/{} successful in {}ms", 
                   sessionId, successCount, files.length, batchDuration.toMillis());
        
        return ResponseEntity.ok(response);
    }

    @GetMapping("/api/upload/status/{trackingId}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getUploadStatus(@PathVariable String trackingId) {
        Map<String, Object> response = new HashMap<>();
        
        ProcessingStatus status = processingStatusMap.get(trackingId);
        if (status == null) {
            response.put("found", false);
            response.put("error", "Tracking ID not found");
            return ResponseEntity.notFound().build();
        }
        
        response.put("found", true);
        response.put("trackingId", trackingId);
        response.put("fileName", status.getFileName());
        response.put("status", status.getStatus());
        response.put("progress", status.getProgress());
        response.put("candidateId", status.getCandidateId());
        response.put("error", status.getError());
        response.put("timestamp", LocalDateTime.now().toString());
        
        // Clean up completed/failed statuses after retrieval
        if ("completed".equals(status.getStatus()) || "failed".equals(status.getStatus())) {
            processingStatusMap.remove(trackingId);
        }
        
        return ResponseEntity.ok(response);
    }

    @PostMapping("/api/match")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> matchCandidates(@RequestParam("jobId") String jobId) {
        Map<String, Object> response = new HashMap<>();
        LocalDateTime matchStartTime = LocalDateTime.now();

        try {
            // Check if AI analysis is enabled
            if (!aiAnalysisEnabled) {
                response.put("success", false);
                response.put("error", "AI analysis is currently disabled");
                return ResponseEntity.status(503).body(response);
            }

            // Validate job exists
            JobRequirement job = candidateService.getJobById(jobId);
            if (job == null) {
                response.put("success", false);
                response.put("error", "Job not found");
                return ResponseEntity.badRequest().body(response);
            }

            logger.info("Matching candidates for job ID: {} ({})", jobId, job.getTitle());
            
            List<MatchResult> matches = candidateService.matchCandidates(jobId);
            
            // Sort matches by score (highest first)
            matches.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
            
            // Calculate match statistics
            Map<String, Object> statistics = new HashMap<>();
            if (!matches.isEmpty()) {
                double avgScore = matches.stream().mapToDouble(MatchResult::getScore).average().orElse(0);
                double maxScore = matches.stream().mapToDouble(MatchResult::getScore).max().orElse(0);
                double minScore = matches.stream().mapToDouble(MatchResult::getScore).min().orElse(0);
                
                statistics.put("averageScore", Math.round(avgScore * 100) / 100.0);
                statistics.put("highestScore", Math.round(maxScore * 100) / 100.0);
                statistics.put("lowestScore", Math.round(minScore * 100) / 100.0);
                statistics.put("excellentMatches", matches.stream().filter(m -> m.getScore() >= 80).count());
                statistics.put("goodMatches", matches.stream().filter(m -> m.getScore() >= 60 && m.getScore() < 80).count());
                statistics.put("fairMatches", matches.stream().filter(m -> m.getScore() < 60).count());
            }

            Duration matchingDuration = Duration.between(matchStartTime, LocalDateTime.now());

            response.put("success", true);
            response.put("matches", matches);
            response.put("totalCandidates", candidateService.getAllCandidates().size());
            response.put("matchCount", matches.size());
            response.put("jobId", jobId);
            response.put("jobTitle", job.getTitle());
            response.put("jobLevel", job.getExperienceLevel());
            response.put("statistics", statistics);
            response.put("processingTime", matchingDuration.toMillis() + "ms");
            response.put("timestamp", LocalDateTime.now().toString());

            logger.info("Found {} matches for job ID: {} in {}ms", 
                       matches.size(), jobId, matchingDuration.toMillis());
            
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
    
    //Queue controllers added
    
    @GetMapping("/api/session/queue-status")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getQueueStatus() {
        try {
            Map<String, Object> queueStatus = sessionManagementService.getQueueStatus();
            queueStatus.put("success", true);
            queueStatus.put("timestamp", LocalDateTime.now().toString());
            return ResponseEntity.ok(queueStatus);
        } catch (Exception e) {
            logger.error("Error getting queue status", e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "Failed to get queue status");
            return ResponseEntity.status(500).body(error);
        }
    }

    @PostMapping("/api/session/join-queue")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> joinQueue(@RequestParam("email") String email) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            String queueId = sessionManagementService.addToQueue(email);
            int position = sessionManagementService.getQueuePosition(queueId);
            
            response.put("success", true);
            response.put("queueId", queueId);
            response.put("position", position);
            response.put("message", "You have been added to the queue at position #" + position);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error joining queue", e);
            response.put("success", false);
            response.put("error", "Failed to join queue");
            return ResponseEntity.status(500).body(response);
        }
    }

    @GetMapping("/api/candidates/{candidateId}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getCandidateDetails(@PathVariable String candidateId) {
        try {
            Candidate candidate = candidateService.getCandidateById(candidateId);
            if (candidate == null) {
                return ResponseEntity.notFound().build();
            }
            
            Map<String, Object> details = new HashMap<>();
            details.put("candidate", candidate);
            details.put("timestamp", LocalDateTime.now().toString());
            
            return ResponseEntity.ok(details);
        } catch (Exception e) {
            logger.error("Error retrieving candidate details for ID: {}", candidateId, e);
            return ResponseEntity.status(500).build();
        }
    }

    @DeleteMapping("/api/candidates/{candidateId}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> deleteCandidate(@PathVariable String candidateId) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            boolean deleted = candidateService.deleteCandidate(candidateId);
            
            if (deleted) {
                response.put("success", true);
                response.put("message", "Candidate deleted successfully");
                logger.info("Candidate deleted: {}", candidateId);
            } else {
                response.put("success", false);
                response.put("error", "Candidate not found");
            }
            
            response.put("timestamp", LocalDateTime.now().toString());
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error deleting candidate: {}", candidateId, e);
            response.put("success", false);
            response.put("error", "Failed to delete candidate: " + e.getMessage());
            response.put("timestamp", LocalDateTime.now().toString());
            return ResponseEntity.status(500).body(response);
        }
    }

    @DeleteMapping("/api/candidates/all")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> clearAllCandidates() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            int deletedCount = candidateService.clearAllCandidates();
            
            response.put("success", true);
            response.put("message", "All candidates cleared successfully");
            response.put("deletedCount", deletedCount);
            response.put("timestamp", LocalDateTime.now().toString());
            
            logger.info("All candidates cleared: {} deleted", deletedCount);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error clearing all candidates", e);
            response.put("success", false);
            response.put("error", "Failed to clear candidates: " + e.getMessage());
            response.put("timestamp", LocalDateTime.now().toString());
            return ResponseEntity.status(500).body(response);
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

    @GetMapping("/api/jobs/{jobId}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getJobDetails(@PathVariable String jobId) {
        try {
            JobRequirement job = candidateService.getJobById(jobId);
            if (job == null) {
                return ResponseEntity.notFound().build();
            }
            
            Map<String, Object> details = new HashMap<>();
            details.put("job", job);
            details.put("timestamp", LocalDateTime.now().toString());
            
            return ResponseEntity.ok(details);
        } catch (Exception e) {
            logger.error("Error retrieving job details for ID: {}", jobId, e);
            return ResponseEntity.status(500).build();
        }
    }

    // ========================================
    // ANALYTICS ENDPOINTS
    // ========================================

    @GetMapping("/api/analytics/summary")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getAnalyticsSummary() {
        Map<String, Object> analytics = new HashMap<>();
        
        try {
            List<Candidate> candidates = candidateService.getAllCandidates();
            
            // Skill distribution
            Map<String, Integer> skillFrequency = new HashMap<>();
            for (Candidate candidate : candidates) {
                if (candidate.getTechnicalSkills() != null) {
                    for (String skill : candidate.getTechnicalSkills()) {
                        skillFrequency.put(skill, skillFrequency.getOrDefault(skill, 0) + 1);
                    }
                }
            }
            
            // Experience level distribution
            Map<String, Long> experienceLevels = new HashMap<>();
            experienceLevels.put("ENTRY", candidates.stream().filter(c -> "ENTRY".equals(c.getExperienceLevel())).count());
            experienceLevels.put("MID", candidates.stream().filter(c -> "MID".equals(c.getExperienceLevel())).count());
            experienceLevels.put("SENIOR", candidates.stream().filter(c -> "SENIOR".equals(c.getExperienceLevel())).count());
            
            analytics.put("totalCandidates", candidates.size());
            analytics.put("totalJobs", candidateService.getAllJobs().size());
            analytics.put("topSkills", getTopSkills(skillFrequency, 10));
            analytics.put("experienceLevelDistribution", experienceLevels);
            analytics.put("timestamp", LocalDateTime.now().toString());
            
            return ResponseEntity.ok(analytics);
            
        } catch (Exception e) {
            logger.error("Error generating analytics summary", e);
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
            features.put("batchUpload", true);
            features.put("analytics", true);
            serviceInfo.put("features", features);
            
            // Statistics
            Map<String, Object> stats = new HashMap<>();
            stats.put("totalCandidates", candidateService.getAllCandidates().size());
            stats.put("totalJobs", candidateService.getAllJobs().size());
            stats.put("activeProcessing", processingStatusMap.size());
            serviceInfo.put("statistics", stats);
            
            // Technology stack
            serviceInfo.put("techStack", List.of("Spring Boot", "AWS S3", "Amazon Textract", "Amazon Bedrock"));
            
            // API endpoints
            List<String> endpoints = List.of(
                "/api/upload", "/api/upload/batch", "/api/match",
                "/api/candidates", "/api/jobs", "/api/analytics/summary"
            );
            serviceInfo.put("endpoints", endpoints);
            
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
            capabilities.put("batchProcessing", "enabled");
            status.put("capabilities", capabilities);
            
            // Quick stats
            status.put("candidateCount", candidateService.getAllCandidates().size());
            status.put("jobCount", candidateService.getAllJobs().size());
            status.put("activeUploads", processingStatusMap.size());
            
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
            components.put("memoryUsage", getMemoryUsage());
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
    // SAMPLE DATA ENDPOINTS
    // ========================================
    
    @GetMapping("/api/sample-resumes/download")
    @ResponseBody
    public ResponseEntity<Resource> downloadSampleResumes() {
        try {
            // Load the zip file from resources
            Resource resource = new ClassPathResource("static/sample-resumes/sample-resumes.zip");
            
            if (!resource.exists()) {
                logger.error("Sample resumes zip file not found");
                return ResponseEntity.notFound().build();
            }
            
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"HR-Agent-Sample-Resumes.zip\"")
                    .header(HttpHeaders.CONTENT_TYPE, "application/zip")
                    .body(resource);
                    
        } catch (Exception e) {
            logger.error("Error downloading sample resumes", e);
            return ResponseEntity.status(500).build();
        }
    }
    
    @GetMapping("/api/sample-resumes/list")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> listSampleResumes() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            List<String> sampleResumes = Arrays.asList(
                "John_Smith_Software_Engineer.pdf",
                "Sarah_Johnson_Data_Scientist.pdf",
                "Michael_Chen_DevOps_Engineer.pdf",
                "Emily_Williams_Angular_Developer.pdf",
                "David_Kumar_Java_Developer.pdf",
                "Lisa_Anderson_Senior_Software_Engineer.pdf",
                "Robert_Martinez_Sales_Analyst.pdf",
                "Jennifer_Lee_Full_Stack_Developer.pdf",
                "William_Brown_Cloud_Architect.pdf",
                "Maria_Garcia_Business_Analyst.pdf"
            );
            
            response.put("success", true);
            response.put("files", sampleResumes);
            response.put("count", sampleResumes.size());
            response.put("description", "Sample resumes for testing HR Agent functionality");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error listing sample resumes", e);
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    // ========================================
    // UTILITY METHODS
    // ========================================

    private boolean isValidFileType(String contentType) {
        return contentType != null && (
                contentType.equals("application/pdf")
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
            Duration duration = Duration.between(startupTime, now);
            
            long days = duration.toDays();
            long hours = duration.toHours() % 24;
            long minutes = duration.toMinutes() % 60;
            long seconds = duration.getSeconds() % 60;
            
            if (days > 0) {
                return String.format("%dd %02d:%02d:%02d", days, hours, minutes, seconds);
            } else {
                return String.format("%02d:%02d:%02d", hours, minutes, seconds);
            }
        } catch (Exception e) {
            return "unknown";
        }
    }

    private String getMemoryUsage() {
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory() / (1024 * 1024);
        long totalMemory = runtime.totalMemory() / (1024 * 1024);
        long freeMemory = runtime.freeMemory() / (1024 * 1024);
        long usedMemory = totalMemory - freeMemory;
        
        return String.format("%dMB / %dMB (%.1f%%)", 
                           usedMemory, maxMemory, 
                           (usedMemory * 100.0) / maxMemory);
    }

    private List<Map<String, Object>> getTopSkills(Map<String, Integer> skillFrequency, int limit) {
        return skillFrequency.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(limit)
                .map(entry -> {
                    Map<String, Object> skill = new HashMap<>();
                    skill.put("name", entry.getKey());
                    skill.put("count", entry.getValue());
                    return skill;
                })
                .toList();
    }

    // Helper method for email masking in logs
    private String maskEmailForLogging(String email) {
        if (email == null || email.length() < 3) return email;
        int atIndex = email.indexOf('@');
        if (atIndex <= 0) return email;
        
        String username = email.substring(0, atIndex);
        String domain = email.substring(atIndex);
        
        if (username.length() <= 2) {
            return username.charAt(0) + "*" + domain;
        } else {
            return username.charAt(0) + "*".repeat(username.length() - 2) + username.charAt(username.length() - 1) + domain;
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
        error.put("message", "An unexpected error occurred. Please try again later.");
        error.put("timestamp", LocalDateTime.now().toString());
        
        return ResponseEntity.status(500).body(error);
    }

    // ========================================
    // INNER CLASSES
    // ========================================

    private static class ProcessingStatus {
        private final String trackingId;
        private final String fileName;
        private String status = "uploading"; // uploading, extracting, analyzing, completed, failed
        private int progress = 0;
        private String candidateId;
        private String error;
        private final LocalDateTime startTime = LocalDateTime.now();

        public ProcessingStatus(String trackingId, String fileName) {
            this.trackingId = trackingId;
            this.fileName = fileName;
        }

        // Getters and setters
        public String getTrackingId() { return trackingId; }
        public String getFileName() { return fileName; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public int getProgress() { return progress; }
        public void setProgress(int progress) { this.progress = progress; }
        public String getCandidateId() { return candidateId; }
        public void setCandidateId(String candidateId) { this.candidateId = candidateId; }
        public String getError() { return error; }
        public void setError(String error) { this.error = error; }
        public LocalDateTime getStartTime() { return startTime; }
    }
}