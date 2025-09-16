package com.dauducbach.identity_service.repository;

import com.dauducbach.identity_service.entity.InvalidatedToken;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

public interface InvalidatedTokenRepository extends ReactiveCrudRepository<InvalidatedToken, String> {
}
