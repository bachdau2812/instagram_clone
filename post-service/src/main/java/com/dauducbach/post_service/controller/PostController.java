package com.dauducbach.post_service.controller;

import com.dauducbach.post_service.dto.request.GetListPostRequest;
import com.dauducbach.post_service.dto.response.CommentResponse;
import com.dauducbach.post_service.dto.response.PostResponse;
import com.dauducbach.post_service.dto.response.UpdatePostRequest;
import com.dauducbach.post_service.service.CommentBroadcastService;
import com.dauducbach.post_service.service.PostService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)

public class PostController {
    CommentBroadcastService commentBroadcastService;
    PostService postService;
    // Comment
    @GetMapping(value = "/sse/comment/{postId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<CommentResponse> streamComments(@PathVariable String postId) {
        return commentBroadcastService.streamCommentForPost(postId);
    }

    @PostMapping("/list-post")
    public Mono<List<PostResponse>> getListPost (@RequestBody GetListPostRequest request) {
        return postService.getListPost(request);
    }

    @GetMapping("/delete-post")
    public Mono<Void> deletePost (@RequestParam String postId) {
        return postService.deletePost(postId);
    }

    @PostMapping("/update-post")
    public Mono<Void> updatePost(@RequestBody UpdatePostRequest request) {
        return postService.updatePost(request);
    }

    @GetMapping("/delete-comment")
    public Mono<Void> deleteComment(@RequestParam String commentId) {
        return postService.deleteComment(commentId);
    }

    @GetMapping("/update-comment")
    public Mono<Void> updateComment(@RequestParam String commentId, @RequestParam String content) {
        return postService.updateComment(commentId, content);
    }
}
