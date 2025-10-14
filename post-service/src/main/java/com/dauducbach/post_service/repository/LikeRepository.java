package com.dauducbach.post_service.repository;

import com.dauducbach.post_service.entity.Like;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface LikeRepository extends ReactiveCrudRepository<Like, String> {
    Mono<Boolean> existsByUserIdAndTargetId(String userId, String targetId);

    Mono<Void> deleteByUserIdAndTargetId(String userId, String targetId);

    Mono<Integer> countLikeByTargetId(String targetId);

    Flux<Like> findAllByTargetId(String targetId);

    Flux<Like> findAllByUserId(String userId);
}
