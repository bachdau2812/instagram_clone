package com.dauducbach.post_service.repository;

import com.dauducbach.post_service.entity.Post;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

public interface PostRepository extends ReactiveCrudRepository<Post, String> {
    Flux<Post> findAllByUserId(String userId);
}
