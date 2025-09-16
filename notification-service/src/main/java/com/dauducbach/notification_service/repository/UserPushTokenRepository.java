package com.dauducbach.notification_service.repository;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;

public interface UserPushTokenRepository extends ReactiveCrudRepository<UserPushTokenRepository, String> {
}
