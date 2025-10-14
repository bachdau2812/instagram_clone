package com.dauducbach.post_service.controller;

import com.dauducbach.post_service.dto.request.GetCommentRequest;
import com.dauducbach.post_service.dto.request.GetPostOfUserRequest;
import com.dauducbach.post_service.dto.response.*;
import com.dauducbach.post_service.service.GetDataOfPostService;
import com.dauducbach.post_service.service.PostService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)

@RequestMapping("/get")
public class GetPostDataController {
    PostService postService;
    GetDataOfPostService getDataOfPostService;

    @GetMapping("/like-list")
    public Flux<LikeResponse> getLikeList(@RequestParam String postId) {
        return postService.getLikeList(postId);
    }

    @PostMapping("/comment-list")
    public Flux<CommentResponse> getCommentList(@RequestBody GetCommentRequest request) {
        return postService.getComment(request);
    }

    @PostMapping("/comment-child")
    public Flux<CommentResponse> getCommentChild(@RequestBody GetCommentRequest request) {
        return postService.getChildComment(request);
    }

    @PostMapping("/all-post-of-user")
    public Flux<PostResponse> getListPostOfUser(@RequestBody GetPostOfUserRequest request) {
        return getDataOfPostService.getListPostOfUser(request);
    }

    @GetMapping("/all-story-of-user")
    public Mono<StoryResponse> getListStoryOfUser(@RequestParam String userId) {
        return getDataOfPostService.getAllStoryOfUser(userId);
    }

    @GetMapping("/like-history-of-user")
    public Mono<LikeHistoryResponse> getLikeHistoryOfUser(
            @RequestParam String userId,
            @RequestParam int currentIndex,
            @RequestParam int limit

    ) {
        return getDataOfPostService.getLikeHistory(userId, currentIndex, limit);
    }

    @GetMapping("/comment-history-of-user")
    public Mono<CommentHistoryResponse> getCommentHistoryOfUser(
            @RequestParam String userId,
            @RequestParam int currentIndex,
            @RequestParam int limit
    ) {
        return getDataOfPostService.getCommentHistory(userId, currentIndex, limit);
    }


}
