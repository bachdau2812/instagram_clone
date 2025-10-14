package com.dauducbach.feed_service.service;

import com.dauducbach.feed_service.dto.request.GetListPostRequest;
import com.dauducbach.feed_service.dto.request.MediaType;
import com.dauducbach.feed_service.dto.response.PostResponse;
import com.dauducbach.feed_service.dto.response.StoryInfo;
import com.dauducbach.feed_service.entity.PostInfo;
import com.dauducbach.feed_service.entity.ProfileIndex;
import com.dauducbach.feed_service.repository.UserFeedRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.domain.Range;
import org.springframework.data.elasticsearch.client.elc.ReactiveElasticsearchTemplate;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RequiredArgsConstructor
@Slf4j

public class LoadFeedService {
    // Constants
    private static final String USER_STORY_KEY_PREFIX = "user_story:";
    private static final String STORY_KEY_PREFIX = "story:";
    private static final String FEED_KEY_PREFIX = "feed:";
    private static final String LAST_POST_KEY_PREFIX = "last_post_of_feed:";
    private static final String POST_KEY_PREFIX = "post:";
    private static final String RECOMMEND_POST_KEY = "recommend_post";
    private static final String FRIEND_RECOMMEND_KEY_PREFIX = "friend_recommend_post:";

    private static final Duration POST_CACHE_TTL = Duration.ofDays(1);

    UserFeedRepository userFeedRepository;
    ReactiveRedisTemplate<String, String> redisString;
    ReactiveRedisTemplate<String, StoryInfo> redisStory;
    ReactiveRedisTemplate<String, PostResponse> redisPost;
    WebClient webClient;
    ReactiveElasticsearchTemplate reactiveElasticsearchTemplate;

    public Flux<StoryInfo> getCurrentStoryListOfUser(String userId, int limit, int currentIndex) {
        if (userId == null || userId.trim().isEmpty()) {
            return Flux.error(new IllegalArgumentException("User ID cannot be null or empty"));
        }
        if (limit <= 0 || currentIndex < 0) {
            return Flux.error(new IllegalArgumentException("Invalid pagination parameters"));
        }

        return redisString.opsForZSet().range(USER_STORY_KEY_PREFIX + userId, Range.of(Range.Bound.inclusive(0L), Range.Bound.unbounded()))
                .skip(currentIndex)
                .take(limit)
                .flatMap(itemId -> redisStory.opsForValue().get(STORY_KEY_PREFIX + itemId));
    }

    public Flux<PostResponse> getFeedForUser(String userId, int limit) {
        return redisString.opsForValue().get(LAST_POST_KEY_PREFIX + userId)
                .flatMapMany(lastPostOfUser -> redisString.opsForZSet().range(FEED_KEY_PREFIX + userId, Range.of(Range.Bound.inclusive(0L), Range.Bound.unbounded()))
                        .collectList()
                        .flatMapMany(listPostFeed -> {
                            int index = listPostFeed.indexOf(lastPostOfUser);
                            if (index == -1) index = 0;

                            int endIndex = Math.min(index + limit, listPostFeed.size());
                            List<String> postIds = listPostFeed.subList(index, endIndex);



                            return Flux.fromIterable(postIds)
                                    .flatMap(postId -> {
                                        Mono<PostResponse> res = redisPost.opsForValue().get(POST_KEY_PREFIX + postId);

                                        return redisString.opsForZSet().remove(FEED_KEY_PREFIX + userId, postId)
                                                .then(res);
                                    })
                                    .onErrorContinue((ex, obj) -> log.warn("Error loading post {}", obj, ex));

                        })
                );
    }

    public Mono<Void> loadFeedForUser(String userId, int limit) {
        // max 50 postId trong zset, min la 21
        return redisString.opsForZSet().size(FEED_KEY_PREFIX + userId)
                .flatMap(size -> {
                    if (size < limit * 2L) {
                        return checkUserFeedDB(userId, limit * 2)
                                .flatMap(postIds -> Flux.fromIterable(postIds)
                                        .flatMap(postId -> redisString.opsForZSet().add(FEED_KEY_PREFIX + userId, postId, Instant.now().toEpochMilli()))
                                        .collectList()
                                        .thenReturn(postIds)
                                );
                    }

                    return redisString.opsForZSet().range(FEED_KEY_PREFIX + userId, Range.of(Range.Bound.inclusive(0L), Range.Bound.unbounded()))
                            .take(limit)
                            .collectList();

                })
                .flatMap(strings -> webClient.post()
                        .uri("http://localhost:8086/post/list-post")
                        .bodyValue(GetListPostRequest.builder()
                                .postIds(strings)
                                .mediaType(MediaType.FEED)
                                .build()
                        )
                        .retrieve()
                        .bodyToMono(new ParameterizedTypeReference<List<PostResponse>>() {})
                        .flatMapMany(Flux::fromIterable)
                        .collectList()
                        .flatMap(post -> {
                            Mono<Void> savePostResponse = Flux.fromIterable(post)
                                    .flatMap(postResponse -> redisPost.opsForValue().set(POST_KEY_PREFIX + postResponse.getPostId(), postResponse,POST_CACHE_TTL))
                                    .collectList()
                                    .then();

                            Mono<Void> saveLastPostId =  redisString.opsForValue().set(LAST_POST_KEY_PREFIX + userId, post.getLast().getPostId()).then();

                            return Mono.zip(savePostResponse, saveLastPostId);
                        })

                )
                .then();
    }

    public Mono<List<String>> checkUserFeedDB(String userId, int limit) {
        return userFeedRepository.findById(userId)
                .flatMap(userFeed -> {
                    List<PostInfo> unreadPosts = userFeed.getFeedList()
                            .stream()
                            .filter(postInfo -> !postInfo.isRead())
                            .toList();

                    if (unreadPosts.size() < limit) {
                        return checkRecommentPostAndAddFeed(userId, limit);
                    }

                    // Lấy list giới hạn theo limit
                    List<PostInfo> res = unreadPosts.stream()
                            .limit(limit)
                            .toList();

                    // Đánh dấu các bài đó là đã đọc trong userFeed
                    userFeed.getFeedList().forEach(postInfo -> {
                        if (res.stream().anyMatch(r -> r.getPostId().equals(postInfo.getPostId()))) {
                            postInfo.setRead(true);
                        }
                    });

                    // Gửi event và lưu lại userFeed
                    return userFeedRepository.save(userFeed)
                            .thenReturn(res.stream().map(PostInfo::getPostId).toList());
                });
    }

    public Mono<List<String>> checkRecommentPostAndAddFeed(String userId, int limit) {
        return redisString.opsForZSet().size(RECOMMEND_POST_KEY)
                .flatMap(size -> {
                    if (size < limit) {
                        return checkFriendRecommend(userId, limit);
                    } else {
                        return getFallbackFromRecommentPost(limit);
                    }
                });
    }
    public Mono<List<String>> getFallbackFromRecommentPost(int limit) {
        return redisString.opsForZSet().range(RECOMMEND_POST_KEY, Range.of(Range.Bound.inclusive(0L), Range.Bound.unbounded()))
                .take(20)
                .collectList();
    }

    public Mono<List<String>> checkFriendRecommend(String userId, int limit) {
        return redisString.opsForZSet().size(FRIEND_RECOMMEND_KEY_PREFIX + userId)
                .flatMap(size -> {
                    if (size < 30) {
                        return loadFeedFromFriendFeed(userId)
                                .then(getFallbackFromFriend(limit, userId));
                    } else {
                        return getFallbackFromFriend(limit, userId);
                    }
                });
    }
    public Mono<List<String>> getFallbackFromFriend(int limit, String userId) {
        return redisString.opsForZSet().popMax(FRIEND_RECOMMEND_KEY_PREFIX + userId, limit)
                .map(ZSetOperations.TypedTuple::getValue)
                .collectList();
    }

    public Mono<Void> loadFeedFromFriendFeed(String userId) {
        return reactiveElasticsearchTemplate.get(userId, ProfileIndex.class, IndexCoordinates.of("profile_aggregate"))
                .flatMap(profileIndex -> {
                    List<String> res = new ArrayList<>();

                    Mono<List<String>> getFromFollower = Flux.fromIterable(profileIndex.getFollowerList())
                            .take(30)
                            .flatMap(followerId -> userFeedRepository.findById(followerId)
                                    .flatMapMany(userFeed -> Flux.fromIterable(userFeed.getFeedList())
                                            .sort((o1, o2) -> Long.compare(o2.getCreateAt(), o1.getCreateAt()))
                                            .take(2)
                                            .map(PostInfo::getPostId)
                                    )
                            )
                            .collectList();

                    Mono<List<String>> getFromFollowing = Flux.fromIterable(profileIndex.getFollowingList())
                            .take(30)
                            .flatMap(followerId -> userFeedRepository.findById(followerId)
                                    .flatMapMany(userFeed -> Flux.fromIterable(userFeed.getFeedList())
                                            .sort((o1, o2) -> Long.compare(o2.getCreateAt(), o1.getCreateAt()))
                                            .take(2)
                                            .map(PostInfo::getPostId)
                                    )
                            )
                            .collectList();

                    return Mono.zip(
                            getFromFollowing,
                            getFromFollower
                    )
                            .flatMap(objects -> {
                                res.addAll(objects.getT1());
                                res.addAll(objects.getT2());

                                return Mono.just(res);
                            });
                })
                .flatMapMany(listPost -> Flux.fromIterable(listPost)
                        .flatMap(postId -> redisString.opsForZSet().add(FRIEND_RECOMMEND_KEY_PREFIX + userId, postId, Instant.now().toEpochMilli()))
                )
                .then();
    }
}

