package com.dauducbach.chat_service.repository;

import com.dauducbach.chat_service.entity.Inbox;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

public interface InboxRepository extends ReactiveCrudRepository<Inbox, String> {
}
