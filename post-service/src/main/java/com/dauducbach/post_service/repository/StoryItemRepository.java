package com.dauducbach.post_service.repository;

import com.dauducbach.post_service.entity.StoryItem;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

public interface StoryItemRepository extends ReactiveCrudRepository<StoryItem, String> {
    Flux<StoryItem> findAllByStoryId(String storyId);
}
