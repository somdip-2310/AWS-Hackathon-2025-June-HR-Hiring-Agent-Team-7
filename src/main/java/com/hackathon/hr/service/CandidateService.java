// src/main/java/com/hackathon/hr/service/CandidateService.java
package com.hackathon.hr.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hackathon.hr.model.Candidate;
import com.hackathon.hr.model.JobRequirement;
import com.hackathon.hr.model.MatchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class CandidateService {

    private static final Logger logger = LoggerFactory.getLogger(CandidateService.class);

    private final S3Service s3Service;
    private final TextractService textractService;
    private final BedrockService bedrockService;
    private final ObjectMapper objectMapper;

    // In-memory storage for hackathon
    private final Map<String, Candidate> candidates = new ConcurrentHashMap<>();
    private final Map<String, JobRequirement> jobRequirements = new ConcurrentHashMap<>();

    public CandidateService(S3Service s3Service, TextractService textractService,
                            BedrockService bedrockService) {
        this.s3Service = s3Service;
        this.textractService = textractService;
        this.bedrockService = bedrockService;
        this.objectMapper = new ObjectMapper();
        initializeSampleJobs();
    }

    public Candidate processResume(MultipartFile file) {
        try {
            // Step 1: Upload to S3
            String s3Key = s3Service.uploadFile(file);

            // Step 2: Extract text with Textract
            String extractedText = textractService.extractText(s3Key);

            // Step 3: Create candidate
            Candidate candidate = new Candidate(file.getOriginalFilename(), extractedText);

            // Step 4: Analyze skills with Bedrock
            analyzeSkills(candidate);

            // Step 5: Store candidate
            candidates.put(candidate.getId(), candidate);

            logger.info("Processed candidate: {}", candidate.getFileName());
            return candidate;

        } catch (Exception e) {
            logger.error("Error processing resume", e);
            throw new RuntimeException("Failed to process resume: " + e.getMessage());
        }
    }

    private void analyzeSkills(Candidate candidate) {
        String prompt = String.format("""
            Analyze the following resume and extract information in JSON format.
            Return ONLY a valid JSON object with this exact structure:
            {
              "technical_skills": ["skill1", "skill2", "skill3"],
              "soft_skills": ["skill1", "skill2", "skill3"],
              "experience_level": "ENTRY",
              "education": "education summary"
            }
            
            Important rules:
            - experience_level must be exactly one of: "ENTRY", "MID", or "SENIOR"
            - Include only the JSON object, no other text
            - Use double quotes for strings
            - Skills should be extracted from the resume text
            
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
            candidate.setTechnicalSkills(Arrays.asList("General Technology"));
            candidate.setSoftSkills(Arrays.asList("Communication", "Problem Solving"));
            candidate.setExperienceLevel("MID");
            candidate.setEducation("Not specified");
        }
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

    public List<MatchResult> matchCandidates(String jobId) {
        JobRequirement job = jobRequirements.get(jobId);
        if (job == null) {
            throw new IllegalArgumentException("Job not found: " + jobId);
        }

        return candidates.values().stream()
                .map(candidate -> calculateMatch(candidate, job))
                .sorted((a, b) -> Double.compare(b.getScore(), a.getScore()))
                .limit(10)
                .collect(Collectors.toList());
    }

    private MatchResult calculateMatch(Candidate candidate, JobRequirement job) {
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

            logger.debug("Calculated match score {} for candidate {} and job {}", 
                    score, candidate.getFileName(), job.getTitle());

            return result;

        } catch (Exception e) {
            logger.error("Error calculating match score for candidate {} and job {}", 
                    candidate.getFileName(), job.getTitle(), e);
            // Return default score if AI fails
            return new MatchResult(candidate.getId(), candidate.getFileName(), 50.0);
        }
    }

    public List<Candidate> getAllCandidates() {
        return new ArrayList<>(candidates.values());
    }

    public List<JobRequirement> getAllJobs() {
        return new ArrayList<>(jobRequirements.values());
    }

    private void initializeSampleJobs() {
        JobRequirement salesDemandPlanning = new JobRequirement(
                "Sales Demand Planning",
                "Analyze sales trends and forecast demand for inventory optimization using statistical modeling and SAP APO",
                Arrays.asList("Statistical Forecasting", "Demand Forecasting", "Inventory Optimization", "SAP APO", "PowerBI"),
                "SENIOR"
        );
        
        JobRequirement angularDeveloper = new JobRequirement(
                "Angular Developer",
                "Develop dynamic web applications using Angular framework with modern TypeScript and responsive design",
                Arrays.asList("Angular", "TypeScript", "HTML", "CSS", "Bootstrap", "RxJS"),
                "MID"
        );
        
        JobRequirement javaDeveloper = new JobRequirement(
                "Java Developer",
                "Build enterprise applications using Java with Spring framework and database integration",
                Arrays.asList("Java", "OOP Concepts", "Collections", "Spring Boot", "Hibernate", "JPA", "REST APIs"),
                "MID"
        );
        
        JobRequirement devOpsEngineer = new JobRequirement(
                "DevOps Engineer",
                "Implement CI/CD pipelines and automate deployment processes using modern DevOps tools",
                Arrays.asList("Jenkins", "GitLab", "GitHub Actions", "SonarCube", "Docker", "Kubernetes", "AWS"),
                "SENIOR"
        );
        
        JobRequirement dataScientist = new JobRequirement(
                "Data Scientist",
                "Analyze complex datasets and build machine learning models to drive business insights",
                Arrays.asList("Python", "Machine Learning", "SQL", "Pandas", "Scikit-learn", "TensorFlow", "Statistics"),
                "MID"
        );

        jobRequirements.put(salesDemandPlanning.getId(), salesDemandPlanning);
        jobRequirements.put(angularDeveloper.getId(), angularDeveloper);
        jobRequirements.put(javaDeveloper.getId(), javaDeveloper);
        jobRequirements.put(devOpsEngineer.getId(), devOpsEngineer);
        jobRequirements.put(dataScientist.getId(), dataScientist);
        
        logger.info("Initialized {} sample jobs", jobRequirements.size());
    }
}