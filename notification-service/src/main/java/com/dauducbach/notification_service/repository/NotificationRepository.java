package com.dauducbach.notification_service.repository;

import com.dauducbach.notification_service.entity.NotificationDB;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

public interface NotificationRepository extends ReactiveCrudRepository<NotificationDB, String> {
    Flux<NotificationDB> findAllByUserId(String userId);
}
