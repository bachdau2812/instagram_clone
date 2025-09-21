package com.dauducbach.notification_service.repository;

import com.dauducbach.notification_service.constant.NotificationEvent;
import com.dauducbach.notification_service.entity.PushConfig;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface PushConfigRepository extends ReactiveCrudRepository<PushConfig, String> {
    Flux<PushConfig> findByUserId(String userId);

    Mono<PushConfig> findByUserIdAndNotificationEvent(String userId, NotificationEvent notificationEvent);
}
