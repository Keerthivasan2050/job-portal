package com.jobportal.dto;

import jakarta.validation.constraints.NotBlank;

public class AiChatRequestDto {

    @NotBlank(message = "Message cannot be empty")
    private String message;

    private String topic = "GENERAL_CAREER"; // RESUME, INTERVIEW, SKILL_GAP, CAREER_ROADMAP, GENERAL_CAREER

    public AiChatRequestDto() {}

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }
}
