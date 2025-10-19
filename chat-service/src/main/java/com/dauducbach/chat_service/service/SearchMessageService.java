package com.dauducbach.chat_service.service;

import com.dauducbach.chat_service.dto.response.MessageResponse;
import com.dauducbach.chat_service.dto.response.SearchMessageResponse;
import com.dauducbach.chat_service.repository.InboxRepository;
import com.dauducbach.chat_service.repository.MessageRepository;
import com.dauducbach.chat_service.repository.UserInboxRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
@FieldDefaults(level = AccessLevel.PRIVATE)
@RequiredArgsConstructor
@Slf4j

public class SearchMessageService {
    MessageRepository messageRepository;
    UserInboxRepository userInboxRepository;
    InboxRepository inboxRepository;

    public Flux<SearchMessageResponse> searchMessage(String query) {
        
    }
}
