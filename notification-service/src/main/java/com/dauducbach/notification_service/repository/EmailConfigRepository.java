package com.dauducbach.notification_service.repository;

import com.dauducbach.notification_service.constant.EmailTypeConfig;
import com.dauducbach.notification_service.entity.EmailConfig;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface EmailConfigRepository extends ReactiveCrudRepository<EmailConfig, String> {
    Flux<EmailConfig> findByUserId(String userId);

    Mono<EmailConfig> findByUserIdAndEmailTypeConfig(String userId, EmailTypeConfig emailTypeConfig);
}
