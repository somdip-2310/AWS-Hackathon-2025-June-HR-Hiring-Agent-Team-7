// src/main/java/com/hackathon/hr/controller/HRController.java
package com.hackathon.hr.controller;

import com.hackathon.hr.model.Candidate;
import com.hackathon.hr.model.JobRequirement;
import com.hackathon.hr.model.MatchResult;
import com.hackathon.hr.service.CandidateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class HRController {
	

    private static final Logger logger = LoggerFactory.getLogger(HRController.class);

    private final CandidateService candidateService;

    public HRController(CandidateService candidateService) {
        this.candidateService = candidateService;
    }

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("candidates", candidateService.getAllCandidates());
        model.addAttribute("jobs", candidateService.getAllJobs());
        return "index";
    }

    @PostMapping("/api/upload")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> uploadResume(@RequestParam("file") MultipartFile file) {
        Map<String, Object> response = new HashMap<>();

        try {
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

            // Process resume
            Candidate candidate = candidateService.processResume(file);

            response.put("success", true);
            response.put("candidateId", candidate.getId());
            response.put("fileName", candidate.getFileName());
            response.put("message", "Resume processed successfully");

            logger.info("Resume uploaded successfully: {}", file.getOriginalFilename());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error uploading resume", e);
            response.put("success", false);
            response.put("error", "Failed to process resume: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    @PostMapping("/api/match")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> matchCandidates(@RequestParam("jobId") String jobId) {
        Map<String, Object> response = new HashMap<>();

        try {
            List<MatchResult> matches = candidateService.matchCandidates(jobId);

            response.put("success", true);
            response.put("matches", matches);
            response.put("totalCandidates", candidateService.getAllCandidates().size());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error matching candidates", e);
            response.put("success", false);
            response.put("error", "Failed to match candidates: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    @GetMapping("/api/candidates")
    @ResponseBody
    public ResponseEntity<List<Candidate>> getCandidates() {
        return ResponseEntity.ok(candidateService.getAllCandidates());
    }

    @GetMapping("/api/jobs")
    @ResponseBody
    public ResponseEntity<List<JobRequirement>> getJobs() {
        return ResponseEntity.ok(candidateService.getAllJobs());
    }

    private boolean isValidFileType(String contentType) {
        return contentType != null && (
                contentType.equals("application/pdf") ||
                        contentType.equals("application/msword") ||
                        contentType.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
        );
    }
}