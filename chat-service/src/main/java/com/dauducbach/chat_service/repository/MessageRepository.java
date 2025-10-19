package com.dauducbach.chat_service.repository;

import com.dauducbach.chat_service.entity.Messages;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

public interface MessageRepository extends ReactiveCrudRepository<Messages, String> {
    Flux<Messages> findAllByInboxUid(String inboxId);
}
