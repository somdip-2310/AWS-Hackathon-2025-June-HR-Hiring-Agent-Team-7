// src/main/java/com/hackathon/hr/model/MatchResult.java
package com.hackathon.hr.model;

public class MatchResult {
    private String candidateId;
    private String candidateName;
    private double score;
    private String analysis;

    public MatchResult(String candidateId, String candidateName, double score) {
        this.candidateId = candidateId;
        this.candidateName = candidateName;
        this.score = score;
    }

    // Getters and Setters
    public String getCandidateId() { return candidateId; }
    public void setCandidateId(String candidateId) { this.candidateId = candidateId; }

    public String getCandidateName() { return candidateName; }
    public void setCandidateName(String candidateName) { this.candidateName = candidateName; }

    public double getScore() { return score; }
    public void setScore(double score) { this.score = score; }

    public String getAnalysis() { return analysis; }
    public void setAnalysis(String analysis) { this.analysis = analysis; }
}