// src/main/java/com/hackathon/hr/model/JobRequirement.java
package com.hackathon.hr.model;

import java.util.List;

public class JobRequirement {
    private String id;
    private String title;
    private String description;
    private List<String> requiredSkills;
    private String experienceLevel;
    private String education;

    // Constructors
    public JobRequirement() {}

    public JobRequirement(String title, String description, List<String> requiredSkills, String experienceLevel) {
        this.id = java.util.UUID.randomUUID().toString();
        this.title = title;
        this.description = description;
        this.requiredSkills = requiredSkills;
        this.experienceLevel = experienceLevel;
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public List<String> getRequiredSkills() { return requiredSkills; }
    public void setRequiredSkills(List<String> requiredSkills) { this.requiredSkills = requiredSkills; }

    public String getExperienceLevel() { return experienceLevel; }
    public void setExperienceLevel(String experienceLevel) { this.experienceLevel = experienceLevel; }

    public String getEducation() { return education; }
    public void setEducation(String education) { this.education = education; }
}