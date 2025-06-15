// src/main/java/com/hackathon/hr/service/CandidateService.java
package com.hackathon.hr.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hackathon.hr.model.Candidate;
import com.hackathon.hr.model.JobRequirement;
import com.hackathon.hr.model.MatchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Service
public class CandidateService {

    private static final Logger logger = LoggerFactory.getLogger(CandidateService.class);

    private final S3Service s3Service;
    private final TextractService textractService;
    private final BedrockService bedrockService;
    private final ObjectMapper objectMapper;
    private final ExecutorService executorService;

    // In-memory storage for hackathon
    private final Map<String, Candidate> candidates = new ConcurrentHashMap<>();
    private final Map<String, JobRequirement> jobRequirements = new ConcurrentHashMap<>();
    
    // Processing status tracking
    private final Map<String, ProcessingStatus> processingStatusMap = new ConcurrentHashMap<>();
    
    // Configuration
    @Value("${candidate.processing.async:false}")
    private boolean asyncProcessing;
    
    @Value("${candidate.batch.max.size:10}")
    private int maxBatchSize;
    
    @Value("${candidate.skills.extraction.enhanced:true}")
    private boolean enhancedSkillsExtraction;

    public CandidateService(S3Service s3Service, TextractService textractService,
                            BedrockService bedrockService) {
        this.s3Service = s3Service;
        this.textractService = textractService;
        this.bedrockService = bedrockService;
        this.objectMapper = new ObjectMapper();
        this.executorService = Executors.newFixedThreadPool(5);
    }
    
    @PostConstruct
    public void init() {
        initializeSampleJobs();
        logger.info("CandidateService initialized with {} sample jobs", jobRequirements.size());
    }
    
 // Add these methods to your CandidateService class

   

 // Replace your existing deleteCandidate and clearAllCandidates methods with these corrected versions

    public boolean deleteCandidate(String candidateId) {
        try {
            // Use the map's remove method instead of removeIf on values()
            Candidate removedCandidate = candidates.remove(candidateId);
            
            if (removedCandidate != null) {
                logger.info("Candidate deleted: {} ({})", candidateId, removedCandidate.getFileName());
                return true;
            } else {
                logger.warn("Candidate not found for deletion: {}", candidateId);
                return false;
            }
            
        } catch (Exception e) {
            logger.error("Error deleting candidate: {}", candidateId, e);
            return false;
        }
    }

    public int clearAllCandidates() {
        try {
            int count = candidates.size();
            candidates.clear();
            
            // Also clean up any processing status entries
            processingStatusMap.clear();
            
            logger.info("All candidates cleared: {} deleted", count);
            return count;
        } catch (Exception e) {
            logger.error("Error clearing all candidates", e);
            throw new RuntimeException("Failed to clear candidates", e);
        }
    }
    
    // ========================================
    // RESUME PROCESSING
    // ========================================

    public Candidate processResume(MultipartFile file) {
        return processResume(file, null);
    }
    
    public Candidate processResume(MultipartFile file, String trackingId) {
        try {
            // Initialize processing status if tracking ID provided
            if (trackingId != null) {
                ProcessingStatus status = new ProcessingStatus(trackingId, file.getOriginalFilename());
                processingStatusMap.put(trackingId, status);
                updateProcessingStatus(trackingId, "uploading", 10);
            }
            
            // Step 1: Upload to S3
            String s3Key = s3Service.uploadFile(file);
            if (trackingId != null) {
                updateProcessingStatus(trackingId, "extracting", 30);
            }

            // Step 2: Extract text with Textract
            String extractedText = textractService.extractText(s3Key);
            if (trackingId != null) {
                updateProcessingStatus(trackingId, "analyzing", 60);
            }

            // Step 3: Create candidate
            Candidate candidate = new Candidate(file.getOriginalFilename(), extractedText);

            // Step 4: Analyze skills with Bedrock
            if (enhancedSkillsExtraction) {
                analyzeSkillsEnhanced(candidate);
            } else {
                analyzeSkills(candidate);
            }
            
            if (trackingId != null) {
                updateProcessingStatus(trackingId, "finalizing", 90);
            }

            // Step 5: Store candidate
            candidates.put(candidate.getId(), candidate);
            
            // Update processing status to completed
            if (trackingId != null) {
                completeProcessing(trackingId, candidate.getId());
            }

            logger.info("Processed candidate: {} with ID: {}", candidate.getFileName(), candidate.getId());
            return candidate;

        } catch (Exception e) {
            logger.error("Error processing resume: {}", file.getOriginalFilename(), e);
            if (trackingId != null) {
                failProcessing(trackingId, e.getMessage());
            }
            throw new RuntimeException("Failed to process resume: " + e.getMessage());
        }
    }
    
    public List<ProcessingResult> processBatch(MultipartFile[] files) {
        List<ProcessingResult> results = new ArrayList<>();
        List<CompletableFuture<ProcessingResult>> futures = new ArrayList<>();
        
        for (MultipartFile file : files) {
            if (files.length > maxBatchSize) {
                results.add(new ProcessingResult(file.getOriginalFilename(), false, 
                    "Batch size exceeds maximum allowed: " + maxBatchSize));
                continue;
            }
            
            CompletableFuture<ProcessingResult> future = CompletableFuture.supplyAsync(() -> {
                try {
                    Candidate candidate = processResume(file);
                    return new ProcessingResult(file.getOriginalFilename(), true, 
                        candidate.getId(), candidate.getTechnicalSkills() != null ? 
                        candidate.getTechnicalSkills().size() : 0);
                } catch (Exception e) {
                    return new ProcessingResult(file.getOriginalFilename(), false, e.getMessage());
                }
            }, executorService);
            
            futures.add(future);
        }
        
        // Wait for all processing to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        
        // Collect results
        for (CompletableFuture<ProcessingResult> future : futures) {
            try {
                results.add(future.get());
            } catch (Exception e) {
                logger.error("Error collecting batch processing result", e);
            }
        }
        
        return results;
    }

    private void analyzeSkills(Candidate candidate) {
        String prompt = String.format("""
            Analyze the following resume and extract information in JSON format.
            Return ONLY a valid JSON object with this exact structure:
            {
              "technical_skills": ["skill1", "skill2", "skill3"],
              "soft_skills": ["skill1", "skill2", "skill3"],
              "experience_level": "ENTRY",
              "education": "education summary",
              "years_of_experience": 0,
              "certifications": ["cert1", "cert2"],
              "industries": ["industry1", "industry2"]
            }
            
            Important rules:
            - experience_level must be exactly one of: "ENTRY", "MID", or "SENIOR"
            - Include only the JSON object, no other text
            - Use double quotes for strings
            - Skills should be extracted from the resume text
            - years_of_experience should be a number
            
            Resume Text:
            %s
            """, candidate.getExtractedText());

        try {
            String response = bedrockService.invokeModel(prompt);
            
            // Clean the response to extract JSON
            String cleanedResponse = extractJsonFromResponse(response);
            
            // Parse JSON response
            Map<String, Object> skillsData = objectMapper.readValue(cleanedResponse,
                    new TypeReference<Map<String, Object>>() {});

            candidate.setTechnicalSkills((List<String>) skillsData.get("technical_skills"));
            candidate.setSoftSkills((List<String>) skillsData.get("soft_skills"));
            candidate.setExperienceLevel((String) skillsData.get("experience_level"));
            candidate.setEducation((String) skillsData.get("education"));

            logger.info("Successfully analyzed skills for candidate: {}", candidate.getFileName());

        } catch (Exception e) {
            logger.error("Error analyzing skills for candidate: {}", candidate.getFileName(), e);
            // Set default values if AI fails
            setDefaultSkills(candidate);
        }
    }
    
    private void analyzeSkillsEnhanced(Candidate candidate) {
        // Enhanced version with more detailed analysis
        String prompt = String.format("""
            Perform a comprehensive analysis of this resume. Extract all relevant information.
            
            Return a JSON object with this structure:
            {
              "technical_skills": ["list all technical skills found"],
              "soft_skills": ["list all soft skills found"],
              "experience_level": "ENTRY/MID/SENIOR",
              "education": "highest education level",
              "years_of_experience": number,
              "certifications": ["list all certifications"],
              "industries": ["list relevant industries"],
              "programming_languages": ["list all programming languages"],
              "frameworks": ["list all frameworks/libraries"],
              "tools": ["list all tools/software"],
              "databases": ["list all databases"],
              "cloud_platforms": ["list cloud platforms"],
              "key_achievements": ["list 2-3 key achievements"],
              "summary": "2-3 sentence professional summary"
            }
            
            Resume Text:
            %s
            """, candidate.getExtractedText());

        try {
            String response = bedrockService.invokeModel(prompt);
            String cleanedResponse = extractJsonFromResponse(response);
            Map<String, Object> skillsData = objectMapper.readValue(cleanedResponse,
                    new TypeReference<Map<String, Object>>() {});

            // Set basic fields
            candidate.setTechnicalSkills((List<String>) skillsData.get("technical_skills"));
            candidate.setSoftSkills((List<String>) skillsData.get("soft_skills"));
            candidate.setExperienceLevel((String) skillsData.get("experience_level"));
            candidate.setEducation((String) skillsData.get("education"));
            
            // Store additional data in a metadata map (you might want to add this to Candidate model)
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("certifications", skillsData.get("certifications"));
            metadata.put("industries", skillsData.get("industries"));
            metadata.put("programming_languages", skillsData.get("programming_languages"));
            metadata.put("frameworks", skillsData.get("frameworks"));
            metadata.put("tools", skillsData.get("tools"));
            metadata.put("databases", skillsData.get("databases"));
            metadata.put("cloud_platforms", skillsData.get("cloud_platforms"));
            metadata.put("key_achievements", skillsData.get("key_achievements"));
            metadata.put("summary", skillsData.get("summary"));
            metadata.put("years_of_experience", skillsData.get("years_of_experience"));
            
            // You might want to add a metadata field to Candidate model
            // candidate.setMetadata(metadata);

            logger.info("Successfully performed enhanced analysis for candidate: {}", candidate.getFileName());

        } catch (Exception e) {
            logger.error("Error in enhanced analysis for candidate: {}", candidate.getFileName(), e);
            // Fall back to basic analysis
            analyzeSkills(candidate);
        }
    }
    
    private void setDefaultSkills(Candidate candidate) {
        candidate.setTechnicalSkills(Arrays.asList("General Technology"));
        candidate.setSoftSkills(Arrays.asList("Communication", "Problem Solving"));
        candidate.setExperienceLevel("MID");
        candidate.setEducation("Not specified");
    }

    private String extractJsonFromResponse(String response) {
        // Remove any text before the first { and after the last }
        int startIndex = response.indexOf('{');
        int endIndex = response.lastIndexOf('}');
        
        if (startIndex != -1 && endIndex != -1 && endIndex > startIndex) {
            return response.substring(startIndex, endIndex + 1);
        }
        
        return response; // Return as-is if no proper JSON structure found
    }

    // ========================================
    // CANDIDATE MATCHING
    // ========================================

    public List<MatchResult> matchCandidates(String jobId) {
        JobRequirement job = jobRequirements.get(jobId);
        if (job == null) {
            throw new IllegalArgumentException("Job not found: " + jobId);
        }

        List<MatchResult> matches = candidates.values().stream()
                .map(candidate -> calculateMatch(candidate, job))
                .filter(match -> match.getScore() > 0) // Filter out zero scores
                .sorted((a, b) -> Double.compare(b.getScore(), a.getScore()))
                .collect(Collectors.toList());
        
        logger.info("Found {} matches for job: {}", matches.size(), job.getTitle());
        return matches;
    }

    private MatchResult calculateMatch(Candidate candidate, JobRequirement job) {
        if (asyncProcessing) {
            return calculateMatchWithAI(candidate, job);
        } else {
            return calculateMatchHeuristic(candidate, job);
        }
    }
    
    private MatchResult calculateMatchWithAI(Candidate candidate, JobRequirement job) {
        String prompt = String.format("""
            Rate this candidate for the job on a scale of 0-100.
            
            Job Title: %s
            Job Description: %s
            Required Skills: %s
            Required Experience: %s
            
            Candidate Skills: %s
            Candidate Experience: %s
            Candidate Education: %s
            
            Consider:
            1. Technical skills match (40%% weight)
            2. Experience level appropriateness (30%% weight)
            3. Education relevance (20%% weight)
            4. Overall fit (10%% weight)
            
            Return ONLY a numeric score between 0 and 100, no additional text or explanation.
            """,
                job.getTitle(),
                job.getDescription(),
                String.join(", ", job.getRequiredSkills()),
                job.getExperienceLevel(),
                candidate.getTechnicalSkills() != null ? String.join(", ", candidate.getTechnicalSkills()) : "None",
                candidate.getExperienceLevel(),
                candidate.getEducation()
        );

        try {
            String response = bedrockService.invokeModel(prompt);
            
            // Extract numeric value from response
            String numericStr = response.trim().replaceAll("[^0-9.]", "");
            double score = Double.parseDouble(numericStr);
            
            // Ensure score is within valid range
            score = Math.max(0, Math.min(100, score));

            MatchResult result = new MatchResult(candidate.getId(),
                    candidate.getFileName(), score);

            logger.debug("AI calculated match score {} for candidate {} and job {}", 
                    score, candidate.getFileName(), job.getTitle());

            return result;

        } catch (Exception e) {
            logger.error("Error calculating AI match score, falling back to heuristic", e);
            return calculateMatchHeuristic(candidate, job);
        }
    }
    
    private MatchResult calculateMatchHeuristic(Candidate candidate, JobRequirement job) {
        double score = 0.0;
        
        // Experience level match (30 points)
        if (candidate.getExperienceLevel() != null && job.getExperienceLevel() != null) {
            if (candidate.getExperienceLevel().equals(job.getExperienceLevel())) {
                score += 30;
            } else if (isExperienceLevelCompatible(candidate.getExperienceLevel(), job.getExperienceLevel())) {
                score += 15;
            }
        }
        
        // Technical skills match (40 points)
        if (candidate.getTechnicalSkills() != null && job.getRequiredSkills() != null) {
            Set<String> candidateSkills = candidate.getTechnicalSkills().stream()
                .map(String::toLowerCase)
                .collect(Collectors.toSet());
            Set<String> requiredSkills = job.getRequiredSkills().stream()
                .map(String::toLowerCase)
                .collect(Collectors.toSet());
            
            long matchedSkills = requiredSkills.stream()
                .filter(candidateSkills::contains)
                .count();
                
            double skillMatchPercentage = requiredSkills.isEmpty() ? 0 : 
                (double) matchedSkills / requiredSkills.size();
            score += skillMatchPercentage * 40;
        }
        
        // Education match (20 points)
        if (candidate.getEducation() != null) {
            score += 20; // Simplified for hackathon
        }
        
        // Soft skills bonus (10 points)
        if (candidate.getSoftSkills() != null && !candidate.getSoftSkills().isEmpty()) {
            score += Math.min(10, candidate.getSoftSkills().size() * 2);
        }
        
        // Ensure score is between 0 and 100
        score = Math.min(100, Math.max(0, score));
        
        MatchResult result = new MatchResult(candidate.getId(),
                candidate.getFileName(), score);
        
        logger.debug("Heuristic calculated match score {} for candidate {} and job {}", 
                score, candidate.getFileName(), job.getTitle());
        
        return result;
    }
    
    private boolean isExperienceLevelCompatible(String candidateLevel, String jobLevel) {
        if (candidateLevel == null || jobLevel == null) {
            return false;
        }
        
        // Senior can do Mid and Entry level jobs
        if ("SENIOR".equals(candidateLevel)) {
            return true;
        }
        
        // Mid can do Entry level jobs
        if ("MID".equals(candidateLevel) && "ENTRY".equals(jobLevel)) {
            return true;
        }
        
        return false;
    }

    // ========================================
    // DATA ACCESS METHODS
    // ========================================

    public List<Candidate> getAllCandidates() {
        return new ArrayList<>(candidates.values());
    }
    
    public Candidate getCandidateById(String candidateId) {
        return candidates.get(candidateId);
    }

    public List<JobRequirement> getAllJobs() {
        return new ArrayList<>(jobRequirements.values());
    }
    
    public JobRequirement getJobById(String jobId) {
        return jobRequirements.get(jobId);
    }
    
   

    // ========================================
    // PROCESSING STATUS METHODS
    // ========================================
    
    public ProcessingStatus getProcessingStatus(String trackingId) {
        return processingStatusMap.get(trackingId);
    }
    
    private void updateProcessingStatus(String trackingId, String status, int progress) {
        ProcessingStatus processingStatus = processingStatusMap.get(trackingId);
        if (processingStatus != null) {
            processingStatus.setStatus(status);
            processingStatus.setProgress(progress);
            processingStatus.setLastUpdated(LocalDateTime.now());
        }
    }
    
    private void completeProcessing(String trackingId, String candidateId) {
        ProcessingStatus processingStatus = processingStatusMap.get(trackingId);
        if (processingStatus != null) {
            processingStatus.setStatus("completed");
            processingStatus.setProgress(100);
            processingStatus.setCandidateId(candidateId);
            processingStatus.setCompletedAt(LocalDateTime.now());
        }
    }
    
    private void failProcessing(String trackingId, String error) {
        ProcessingStatus processingStatus = processingStatusMap.get(trackingId);
        if (processingStatus != null) {
            processingStatus.setStatus("failed");
            processingStatus.setError(error);
            processingStatus.setCompletedAt(LocalDateTime.now());
        }
    }
    
    public void cleanupProcessingStatus(String trackingId) {
        processingStatusMap.remove(trackingId);
    }

    // ========================================
    // INITIALIZATION
    // ========================================

    private void initializeSampleJobs() {
        // Job 1 - Matches Aarav Sharma (Junior Software Developer)
        JobRequirement job1 = new JobRequirement();
        job1.setId("1");
        job1.setTitle("Junior Frontend Developer");
        job1.setDescription("Seeking a passionate Junior Software Developer with 2+ years of experience in React and JavaScript. Must have hands-on experience with Node.js, MongoDB, and Express.js.");
        job1.setRequiredSkills(Arrays.asList("React", "JavaScript", "Node.js", "MongoDB", "Express.js", "HTML5", "CSS3", "Bootstrap", "Git", "JWT", "REST APIs"));
        job1.setExperienceLevel("ENTRY");
        jobRequirements.put(job1.getId(), job1);
        
        // Job 2 - Matches Anjali Patel (Data Analyst)
        JobRequirement job2 = new JobRequirement();
        job2.setId("2");
        job2.setTitle("Data Analyst - Tech & Analytics");
        job2.setDescription("Looking for an experienced Data Analyst with strong skills in Python, SQL, and data visualization. Experience with machine learning and statistical modeling is highly valued.");
        job2.setRequiredSkills(Arrays.asList("Python", "R", "SQL", "Tableau", "Power BI", "Pandas", "NumPy", "Scikit-learn", "TensorFlow", "AWS", "Machine Learning"));
        job2.setExperienceLevel("MID");
        jobRequirements.put(job2.getId(), job2);
        
        // Job 3 - Matches Arjun Singh (Senior Full Stack Developer)
        JobRequirement job3 = new JobRequirement();
        job3.setId("3");
        job3.setTitle("Senior Full Stack Developer");
        job3.setDescription("We need a Senior Full Stack Developer with expertise in React, Node.js, and cloud technologies. Experience with microservices architecture and TypeScript is essential.");
        job3.setRequiredSkills(Arrays.asList("React", "Node.js", "TypeScript", "MongoDB", "PostgreSQL", "Docker", "Kubernetes", "AWS", "Microservices", "Python"));
        job3.setExperienceLevel("SENIOR");
        jobRequirements.put(job3.getId(), job3);
        
        // Job 4 - Matches Deepika Reddy (Java Developer)
        JobRequirement job4 = new JobRequirement();
        job4.setId("4");
        job4.setTitle("Java Backend Developer");
        job4.setDescription("Seeking experienced Java Developer with Spring Boot expertise for enterprise applications. Must have strong microservices and database knowledge.");
        job4.setRequiredSkills(Arrays.asList("Java", "Spring Boot", "Spring MVC", "Hibernate", "PostgreSQL", "MySQL", "Docker", "Kafka", "Microservices", "REST API"));
        job4.setExperienceLevel("MID");
        jobRequirements.put(job4.getId(), job4);
        
        // Job 5 - Matches Karan Singh (DevOps Engineer)
        JobRequirement job5 = new JobRequirement();
        job5.setId("5");
        job5.setTitle("DevOps Engineer - Cloud Infrastructure");
        job5.setDescription("Looking for a skilled DevOps Engineer to manage our AWS infrastructure and CI/CD pipelines. Experience with Kubernetes and Infrastructure as Code is required.");
        job5.setRequiredSkills(Arrays.asList("AWS", "Docker", "Kubernetes", "Jenkins", "Terraform", "Ansible", "Prometheus", "Grafana", "CI/CD", "Linux"));
        job5.setExperienceLevel("MID");
        jobRequirements.put(job5.getId(), job5);
        
        // Job 6 - Matches Kavya Nair (Full Stack Developer)
        JobRequirement job6 = new JobRequirement();
        job6.setId("6");
        job6.setTitle("Full Stack Developer - React/Django");
        job6.setDescription("Join our team as a Full Stack Developer working with React and Django/Python. Experience with multiple databases and cloud deployment required.");
        job6.setRequiredSkills(Arrays.asList("React", "Vue.js", "Node.js", "Python", "Django", "PostgreSQL", "MySQL", "MongoDB", "TypeScript", "AWS"));
        job6.setExperienceLevel("MID");
        jobRequirements.put(job6.getId(), job6);
        
        // Job 7 - Matches Mei Chen (Marketing Analyst)
        JobRequirement job7 = new JobRequirement();
        job7.setId("7");
        job7.setTitle("Marketing Data Analyst");
        job7.setDescription("Seeking a data-driven Marketing Analyst to analyze cross-cultural campaigns and customer insights across Asia-Pacific markets.");
        job7.setRequiredSkills(Arrays.asList("Data Analytics", "SQL", "Python", "Tableau", "Power BI", "Google Analytics", "A/B Testing", "Marketing Analytics"));
        job7.setExperienceLevel("MID");
        jobRequirements.put(job7.getId(), job7);
        
        // Job 8 - Matches Priya Sharma (Sales & Marketing Professional)
        JobRequirement job8 = new JobRequirement();
        job8.setId("8");
        job8.setTitle("Senior Marketing Manager - Digital");
        job8.setDescription("Lead our digital marketing initiatives across India. Need expertise in B2B sales, CRM management, and multi-channel campaign strategy.");
        job8.setRequiredSkills(Arrays.asList("Digital Marketing", "CRM Management", "Lead Generation", "Google Ads", "HubSpot", "Campaign Strategy", "B2B Sales"));
        job8.setExperienceLevel("SENIOR");
        jobRequirements.put(job8.getId(), job8);
        
        // Job 9 - Matches Rajesh Kumar (Demand Planning Specialist)
        JobRequirement job9 = new JobRequirement();
        job9.setId("9");
        job9.setTitle("Demand Planning Manager - Supply Chain");
        job9.setDescription("Strategic role in demand planning and supply chain optimization. SAP IBP experience and statistical forecasting skills required.");
        job9.setRequiredSkills(Arrays.asList("SAP IBP", "SAP APO", "Demand Planning", "Supply Chain", "Statistical Forecasting", "Excel", "Python", "R", "S&OP"));
        job9.setExperienceLevel("SENIOR");
        jobRequirements.put(job9.getId(), job9);
        
        // Job 10 - Matches Rohit Gupta (Angular Developer)
        JobRequirement job10 = new JobRequirement();
        job10.setId("10");
        job10.setTitle("Senior Angular Developer");
        job10.setDescription("Looking for an experienced Angular Developer to build enterprise banking applications. Strong TypeScript and state management experience required.");
        job10.setRequiredSkills(Arrays.asList("Angular", "TypeScript", "RxJS", "NgRx", "Jasmine", "Karma", "REST APIs", "Angular Material", "Bootstrap"));
        job10.setExperienceLevel("MID");
        jobRequirements.put(job10.getId(), job10);
        
        logger.info("Initialized {} sample jobs matching candidate profiles", jobRequirements.size());
    }
    
    // ========================================
    // SHUTDOWN
    // ========================================
    
    public void shutdown() {
        executorService.shutdown();
        logger.info("CandidateService shutting down");
    }
    
    // ========================================
    // INNER CLASSES
    // ========================================
    
    public static class ProcessingStatus {
        private final String trackingId;
        private final String fileName;
        private String status = "initializing";
        private int progress = 0;
        private String candidateId;
        private String error;
        private final LocalDateTime startedAt = LocalDateTime.now();
        private LocalDateTime lastUpdated = LocalDateTime.now();
        private LocalDateTime completedAt;
        
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
        public LocalDateTime getStartedAt() { return startedAt; }
        public LocalDateTime getLastUpdated() { return lastUpdated; }
        public void setLastUpdated(LocalDateTime lastUpdated) { this.lastUpdated = lastUpdated; }
        public LocalDateTime getCompletedAt() { return completedAt; }
        public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
    }
    
    public static class ProcessingResult {
        private final String fileName;
        private final boolean success;
        private final String candidateId;
        private final String error;
        private final int skillsExtracted;
        
        public ProcessingResult(String fileName, boolean success, String candidateId, int skillsExtracted) {
            this.fileName = fileName;
            this.success = success;
            this.candidateId = candidateId;
            this.error = null;
            this.skillsExtracted = skillsExtracted;
        }
        
        public ProcessingResult(String fileName, boolean success, String error) {
            this.fileName = fileName;
            this.success = success;
            this.candidateId = null;
            this.error = error;
            this.skillsExtracted = 0;
        }
        
        // Getters
        public String getFileName() { return fileName; }
        public boolean isSuccess() { return success; }
        public String getCandidateId() { return candidateId; }
        public String getError() { return error; }
        public int getSkillsExtracted() { return skillsExtracted; }
    }
}