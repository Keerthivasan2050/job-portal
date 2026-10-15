package com.jobportal.dto;

import java.util.List;

public class AiChatResponseDto {

    private String reply;
    private String source; // e.g., "AI Provider: Gemini 1.5 Flash" or "Intelligent Rule-Based Knowledge Engine"
    private List<String> suggestions;

    public AiChatResponseDto() {}

    public AiChatResponseDto(String reply, String source, List<String> suggestions) {
        this.reply = reply;
        this.source = source;
        this.suggestions = suggestions;
    }

    public String getReply() { return reply; }
    public void setReply(String reply) { this.reply = reply; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public List<String> getSuggestions() { return suggestions; }
    public void setSuggestions(List<String> suggestions) { this.suggestions = suggestions; }
}
