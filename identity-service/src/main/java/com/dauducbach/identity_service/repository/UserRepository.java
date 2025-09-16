package com.dauducbach.identity_service.repository;

import com.dauducbach.identity_service.entity.User;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

public interface UserRepository extends ReactiveCrudRepository<User, String> {
    Mono<Boolean> existsByUsername(String username);

    Mono<Boolean> existsByEmail(String email);

    Mono<User> findByEmail(String email);

    Mono<User> findByUsername(String username);

    Mono<Boolean> existsByProviderId(String providerId);

    Mono<User> findByProviderId(String providerId);
}
