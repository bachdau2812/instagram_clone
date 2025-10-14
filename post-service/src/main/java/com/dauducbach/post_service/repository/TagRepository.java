package com.dauducbach.post_service.repository;

import com.dauducbach.post_service.entity.Tag;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface TagRepository extends ReactiveCrudRepository<Tag, String> {
    Flux<Tag> findByPostId(String postId);

    Mono<Void> deleteByPostId(String postId);
}
