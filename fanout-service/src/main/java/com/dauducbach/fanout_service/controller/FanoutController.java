package com.dauducbach.fanout_service.controller;

import com.dauducbach.fanout_service.dto.request.*;
import com.dauducbach.fanout_service.service.*;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)

public class FanoutController {
    FanoutPostCreation fanoutPostCreation;
    FanoutForLikePost fanoutForLikePost;
    FanoutForProfileOperation fanoutForProfileOperation;
    FanoutForCommentPost fanoutForCommentPost;
    FanoutForUploadStory fanoutForUploadStory;

    @PostMapping("/operation")
    Mono<Void> profileOperator(@RequestBody ProfileOperationRequest request) {
        return fanoutForProfileOperation.profileOperation(request);
    }

    @PostMapping(value = "/post-create", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    Mono<Void> postCreation(
            @RequestPart("userId") String userId,
            @RequestPart("content") String content,
            @RequestPart("tags") List<String> tags,
            @RequestPart("media") List<FilePart> media
    ) {
        return fanoutPostCreation.createPost(PostCreationRequest.builder()
                        .userId(userId)
                        .content(content)
                        .tags(tags)
                        .media(media)
                .build());
    }

    @PostMapping("/like-post")
    Mono<Void> likePost(@RequestBody LikePostRequest request) {
        return fanoutForLikePost.likePost(request);
    }

    @PostMapping("/comment-post")
    Mono<Void> commentPost(@RequestBody CommentRequest request) {
        return fanoutForCommentPost.commentPost(request);
    }

    @PostMapping(value = "/up-story", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    Mono<Void> upStory(
            @RequestPart String userId,
            @RequestPart FilePart media,
            @RequestPart String audioDisplayName
    ) {
        return fanoutForUploadStory.upStory(StoryUploadRequest.builder()
                        .userId(userId)
                        .media(media)
                        .audioDisplayName(audioDisplayName)
                .build());
    }
}
