package com.dauducbach.identity_service.repository;

import com.dauducbach.identity_service.entity.UserRoles;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

public interface UserRoleRepository extends ReactiveCrudRepository<UserRoles, String> {
    Flux<UserRoles> findByUserId(String userId);
}
