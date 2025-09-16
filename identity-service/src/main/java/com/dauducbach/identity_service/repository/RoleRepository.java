package com.dauducbach.identity_service.repository;

import com.dauducbach.identity_service.entity.Role;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

public interface RoleRepository extends ReactiveCrudRepository<Role, String> {
}
