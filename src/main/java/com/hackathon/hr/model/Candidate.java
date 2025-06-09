// src/main/java/com/hackathon/hr/model/Candidate.java
package com.hackathon.hr.model;

import java.time.LocalDateTime;
import java.util.List;

public class Candidate {
    private String id;
    private String fileName;
    private String extractedText;
    private List<String> technicalSkills;
    private List<String> softSkills;
    private String experienceLevel;
    private String education;
    private LocalDateTime processedAt;

    // Constructors
    public Candidate() {}

    public Candidate(String fileName, String extractedText) {
        this.id = java.util.UUID.randomUUID().toString();
        this.fileName = fileName;
        this.extractedText = extractedText;
        this.processedAt = LocalDateTime.now();
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public String getExtractedText() { return extractedText; }
    public void setExtractedText(String extractedText) { this.extractedText = extractedText; }

    public List<String> getTechnicalSkills() { return technicalSkills; }
    public void setTechnicalSkills(List<String> technicalSkills) { this.technicalSkills = technicalSkills; }

    public List<String> getSoftSkills() { return softSkills; }
    public void setSoftSkills(List<String> softSkills) { this.softSkills = softSkills; }

    public String getExperienceLevel() { return experienceLevel; }
    public void setExperienceLevel(String experienceLevel) { this.experienceLevel = experienceLevel; }

    public String getEducation() { return education; }
    public void setEducation(String education) { this.education = education; }

    public LocalDateTime getProcessedAt() { return processedAt; }
    public void setProcessedAt(LocalDateTime processedAt) { this.processedAt = processedAt; }
}