package com.jobportal.service;

import com.jobportal.dto.AiChatRequestDto;
import com.jobportal.dto.AiChatResponseDto;
import com.jobportal.entity.ChatMessage;
import com.jobportal.entity.User;

import java.util.List;

public interface AiCareerAssistantService {
    AiChatResponseDto generateCareerAdvice(User user, AiChatRequestDto request);
    List<ChatMessage> getChatHistory(User user);
    void clearChatHistory(User user);
}
