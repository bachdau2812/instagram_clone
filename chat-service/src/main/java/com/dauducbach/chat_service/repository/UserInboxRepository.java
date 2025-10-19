package com.dauducbach.chat_service.repository;

import com.dauducbach.chat_service.entity.UserInbox;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface UserInboxRepository extends ReactiveCrudRepository<UserInbox, String> {
    Mono<Boolean> existsByUserIdAndInboxId(String userId, String inboxId);

    Mono<UserInbox> findByUserIdAndInboxId(String userId, String inboxId);

    Flux<UserInbox> findAllByInboxId(String inboxId);

    Mono<Void> deleteByUserIdAndInboxId(String userId, String inboxId);

    Flux<UserInbox> findAllByUserId(String userId);
}
