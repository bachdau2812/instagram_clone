package com.dauducbach.feed_service.controller;

import com.dauducbach.feed_service.dto.response.PostResponse;
import com.dauducbach.feed_service.dto.response.StoryInfo;
import com.dauducbach.feed_service.service.LoadFeedService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)

public class FeedController {
    LoadFeedService loadFeedService;

    @GetMapping("/get-story")
    public Flux<StoryInfo> getStory(
            @RequestParam String userId,
            @RequestParam int currentIndex,
            @RequestParam int limit
    ) {
        return loadFeedService.getCurrentStoryListOfUser(userId, limit, currentIndex);
    }

    @GetMapping("load-feed")
    public Mono<Void> loadFeed(
            @RequestParam String userId,
            @RequestParam int currentIndex
    ) {
        return loadFeedService.loadFeedForUser(userId, currentIndex);
    }

    @GetMapping("/get-feed")
    public Flux<PostResponse> getFeed(
            @RequestParam String userId,
            @RequestParam int limit
    ) {
        return loadFeedService.getFeedForUser(userId, limit);
    }
}
