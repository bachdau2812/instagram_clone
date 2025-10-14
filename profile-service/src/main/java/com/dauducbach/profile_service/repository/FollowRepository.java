package com.dauducbach.profile_service.repository;

import com.dauducbach.profile_service.entity.Follow;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface FollowRepository extends ReactiveCrudRepository<Follow, String> {
    Flux<Follow> findAllByFollowerId(String followerId);

    Flux<Follow> findAllByFollowingId(String followingId);

    Mono<Follow> findByFollowerIdAndFollowingId(String followerId, String followingId);

    Mono<Boolean> existsByFollowerIdAndFollowingId(String followerId, String followingId);

    Mono<Void> deleteByFollowerIdAndFollowingId(String followerId, String followingId);

}
