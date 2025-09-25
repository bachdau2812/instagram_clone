package com.dauducbach.profile_service.repository;

import com.dauducbach.profile_service.entity.Block;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface BlockRepository extends ReactiveCrudRepository<Block, String> {
    Flux<Block> findByBlockerId(String blockerId);

    Mono<Void> deleteByBlockerIdAndBlockingId(String blockerId, String blockingId);
}
