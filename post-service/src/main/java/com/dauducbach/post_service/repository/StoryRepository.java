package com.dauducbach.post_service.repository;

import com.dauducbach.post_service.entity.Story;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

public interface StoryRepository extends ReactiveCrudRepository<Story, String> {
    Mono<Story> findByUserId(String userId);

    Mono<Boolean> existsByUserId(String userId);
}
