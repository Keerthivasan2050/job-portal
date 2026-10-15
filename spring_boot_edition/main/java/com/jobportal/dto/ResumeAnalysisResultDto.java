package com.jobportal.dto;

import java.util.List;

public class ResumeAnalysisResultDto {

    private int overallScore; // 0 - 100 %
    private List<String> matchedSkills;
    private List<String> missingSkills;
    private List<String> strengths;
    private List<String> suggestions;
    private List<String> formattingFeedback;

    public ResumeAnalysisResultDto() {}

    public int getOverallScore() { return overallScore; }
    public void setOverallScore(int overallScore) { this.overallScore = overallScore; }

    public List<String> getMatchedSkills() { return matchedSkills; }
    public void setMatchedSkills(List<String> matchedSkills) { this.matchedSkills = matchedSkills; }

    public List<String> getMissingSkills() { return missingSkills; }
    public void setMissingSkills(List<String> missingSkills) { this.missingSkills = missingSkills; }

    public List<String> getStrengths() { return strengths; }
    public void setStrengths(List<String> strengths) { this.strengths = strengths; }

    public List<String> getSuggestions() { return suggestions; }
    public void setSuggestions(List<String> suggestions) { this.suggestions = suggestions; }

    public List<String> getFormattingFeedback() { return formattingFeedback; }
    public void setFormattingFeedback(List<String> formattingFeedback) { this.formattingFeedback = formattingFeedback; }
}
