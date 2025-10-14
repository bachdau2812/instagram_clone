package com.dauducbach.storage_service.repository;

import com.dauducbach.storage_service.entity.Media;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

import java.util.List;

public interface MediaRepository extends ReactiveCrudRepository<Media, String> {
    Mono<Void> deleteByPublicId(String publicId);

    Mono<Media> findByOwnerId(String ownerId);

    Mono<Media> findByDisplayName(String displayName);
}
