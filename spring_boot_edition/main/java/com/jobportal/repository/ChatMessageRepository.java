package com.jobportal.repository;

import com.jobportal.entity.ChatMessage;
import com.jobportal.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findByUserOrderByTimestampAsc(User user);
    void deleteByUser(User user);
}
