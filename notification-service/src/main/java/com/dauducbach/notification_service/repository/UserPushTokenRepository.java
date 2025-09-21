package com.dauducbach.notification_service.repository;

import com.dauducbach.notification_service.entity.UserPushToken;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

public interface UserPushTokenRepository extends ReactiveCrudRepository<UserPushToken, String> {

    Mono<UserPushToken> findByUserId(String userId);

}
