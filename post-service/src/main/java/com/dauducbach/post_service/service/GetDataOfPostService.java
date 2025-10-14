package com.dauducbach.post_service.service;

import com.dauducbach.post_service.constant.MediaType;
import com.dauducbach.post_service.dto.request.GetFileRequest;
import com.dauducbach.post_service.dto.request.GetPostOfUserRequest;
import com.dauducbach.post_service.dto.request.UserBasicInfoRequest;
import com.dauducbach.post_service.dto.response.*;
import com.dauducbach.post_service.entity.Like;
import com.dauducbach.post_service.entity.Post;
import com.dauducbach.post_service.repository.*;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RequiredArgsConstructor
@Slf4j

public class GetDataOfPostService {
    PostRepository postRepository;
    LikeRepository likeRepository;
    CommentRepository commentRepository;
    StoryRepository storyRepository;
    StoryItemRepository storyItemRepository;
    PostService postService;
    WebClient webClient;

    public Flux<PostResponse> getListPostOfUser(GetPostOfUserRequest request) {
        return ReactiveSecurityContextHolder.getContext()
                .map(securityContext -> ((Jwt) securityContext.getAuthentication().getPrincipal()).getTokenValue())
                .flatMapMany(token -> postRepository.findAllByUserId(request.getUserId())
                        .sort((a,b) -> Double.compare(b.getCreateAt().toEpochMilli(), a.getCreateAt().toEpochMilli()))
                        .skip(request.getCurrentIndex())
                        .take(request.getLimit())
                        .flatMap(post -> postService.createPostResponse(post, token, MediaType.PROFILE))
                );
    }

    public Mono<StoryResponse> getAllStoryOfUser(String userId) {
        StringBuilder sb = new StringBuilder();

        return storyRepository.findByUserId(userId)
                .flatMapMany(story -> {
                    sb.append(story.getId());
                    return storyItemRepository.findAllByStoryId(story.getId());
                })
                .map(storyItem -> StoryItemResponse.builder()
                        .itemId(storyItem.getId())
                        .mediaUrl(storyItem.getAudioUrl())
                        .createAt(storyItem.getCreateAt().toEpochMilli())
                        .isExpired(storyItem.isExpired())
                        .audioUrl(storyItem.getAudioUrl())
                        .build()
                )
                .collectList()
                .map(storyItemResponses -> StoryResponse.builder()
                        .userid(userId)
                        .storyId(sb.toString())
                        .items(storyItemResponses)
                        .build()
                );
    }

    public Mono<LikeHistoryResponse> getLikeHistory(String userId, int currentIndex, int limit) {
        return ReactiveSecurityContextHolder.getContext()
                .map(securityContext -> ((Jwt) securityContext.getAuthentication().getPrincipal()).getTokenValue())
                .flatMapMany(token -> likeRepository.findAllByUserId(userId)
                        .sort((a,b) -> Double.compare(b.getTimestamp().toEpochMilli(), a.getTimestamp().toEpochMilli()))
                        .skip(currentIndex)
                        .take(limit)
                )
                .flatMap(like -> postRepository.findById(like.getTargetId())
                        .flatMap(post -> {
                            var likeItemResponse = LikeItemResponse.builder()
                                    .postId(post.getId())
                                    .postOfUserId(post.getUserId())
                                    .build();

                            // Lay thong tin co ban
                            Mono<UserInfoLikeResponse> info = webClient.post()
                            .uri("http://localhost:8081/profile/get-like-info")
                            .bodyValue(UserBasicInfoRequest.builder()
                                    .userId(List.of(likeItemResponse.getPostOfUserId()))
                            )
                            .retrieve()
                            .bodyToFlux(UserInfoLikeResponse.class)
                            .collectList()
                            .map(ls -> ls.get(0));

                            // Lay anh dau tien cua bai viet
                            Mono<String> img = webClient.post()
                                    .uri("http://localhost:8084/storage/get/medias")
                                    .bodyValue(GetFileRequest.builder()
                                            .ownerId(likeItemResponse.getPostId())
                                            .mediaType(MediaType.PROFILE)
                                            .build()
                                    )
                                    .retrieve()
                                    .bodyToMono(new ParameterizedTypeReference<List<String>>() {})
                                    .map(list -> list.get(0));


                            return Mono.zip(info, img)
                                    .map(tuple -> {
                                        likeItemResponse.setFirstImg(tuple.getT1().getDisplayName());
                                        likeItemResponse.setPostOfUsername(tuple.getT2());

                                        return likeItemResponse;
                                    });
                        })
                )
                .collectList()
                .map(likeItemResponses -> LikeHistoryResponse.builder()
                        .userId(userId)
                        .content("da thich bai viet cua")
                        .responses(likeItemResponses)
                        .build()
                );
    }

    public Mono<CommentHistoryResponse> getCommentHistory(String userId, int limit, int currentIndex) {
        return commentRepository.findAllByUserId(userId)
                .flatMap(comment -> postRepository.findById(comment.getPostId())
                        .flatMap(post -> {
                            var item = CommentItemResponse.builder()
                                    .commentId(comment.getId())
                                    .postId(post.getId())
                                    .postOfUserId(post.getUserId())
                                    .content(comment.getContent())
                                    .build();

                            // Lay thong tin co ban
                            Mono<UserInfoLikeResponse> info = webClient.post()
                                    .uri("http://localhost:8081/profile/get-like-info")
                                    .bodyValue(UserBasicInfoRequest.builder()
                                            .userId(List.of(item.getPostOfUserId()))
                                    )
                                    .retrieve()
                                    .bodyToFlux(UserInfoLikeResponse.class)
                                    .collectList()
                                    .map(ls -> ls.get(0));

                            // Lay anh dau tien cua bai viet
                            Mono<String> img = webClient.post()
                                    .uri("http://localhost:8084/storage/get/medias")
                                    .bodyValue(GetFileRequest.builder()
                                            .ownerId(item.getPostId())
                                            .mediaType(MediaType.PROFILE)
                                            .build()
                                    )
                                    .retrieve()
                                    .bodyToMono(new ParameterizedTypeReference<List<String>>() {})
                                    .map(list -> list.get(0));

                            return Mono.zip(info, img)
                                    .map(tuple -> {
                                        item.setFirstImg(tuple.getT1().getDisplayName());
                                        item.setPostOfUsername(tuple.getT2());

                                        return item;
                                    });
                        })
                ).collectList()
                .map(commentItemResponses -> CommentHistoryResponse.builder()
                        .userId(userId)
                        .commentItemResponses(commentItemResponses)
                        .build()
                );
    }
}
