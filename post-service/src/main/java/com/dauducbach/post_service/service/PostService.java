package com.dauducbach.post_service.service;

import com.dauducbach.event.UpdatePostCommand;
import com.dauducbach.post_service.constant.MediaType;
import com.dauducbach.post_service.dto.request.*;
import com.dauducbach.post_service.dto.response.*;
import com.dauducbach.post_service.entity.Like;
import com.dauducbach.post_service.entity.Post;
import com.dauducbach.post_service.entity.Tag;
import com.dauducbach.post_service.repository.CommentRepository;
import com.dauducbach.post_service.repository.LikeRepository;
import com.dauducbach.post_service.repository.PostRepository;
import com.dauducbach.post_service.repository.TagRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderRecord;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j

public class PostService {
    PostRepository postRepository;
    LikeRepository likeRepository;
    CommentRepository commentRepository;
    WebClient webClient;
    TagRepository tagRepository;
    KafkaSender<String, Object> kafkaSender;
    R2dbcEntityTemplate r2dbcEntityTemplate;

    // Cac thao tac voi post

    public Mono<PostResponse> getPost(GetPostRequest request) {
        return ReactiveSecurityContextHolder.getContext()
                .map(securityContext -> securityContext.getAuthentication().getPrincipal())
                .map(principal -> ((Jwt) principal).getTokenValue())
                .flatMap(token -> postRepository.findById(request.getPostId())
                        .flatMap(post -> createPostResponse(post, token, request.getMediaType()))
                );
    }

    public Mono<PostResponse> createPostResponse(Post post, String token, MediaType mediaType) {
        Mono<List<String>> mediaOfPost = webClient.post()
                .uri("http://localhost:8084/storage/get/medias")
                .bodyValue(GetFileRequest.builder()
                        .ownerId(post.getId())
                        .mediaType(mediaType)
                        .build()
                )
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<String>>() {});

        Mono<String> avatarOfPost = webClient.get()
                .uri("http://localhost:8084/storage/get/avt-feed?ownerId=" + post.getUserId())
                .retrieve()
                .bodyToMono(String.class);

        Mono<List<String>> tags = tagRepository.findByPostId(post.getId())
                .map(Tag::getContent)
                .collectList();


        Mono<Integer> likeCount = likeRepository.countLikeByTargetId(post.getId());

        Mono<Integer> commentCount = commentRepository.countCommentByPostId(post.getId());

        return Mono.zip(
                        mediaOfPost,
                        avatarOfPost,
                        tags,
                        likeCount,
                        commentCount)
                .map(tuple -> {
                    List<String> medias = tuple.getT1();
                    String avatar = tuple.getT2();
                    List<String> tagList = tuple.getT3();
                    int likes = tuple.getT4();
                    int comments = tuple.getT5();

                    return PostResponse.builder()
                            .postId(post.getId())
                            .userId(post.getUserId())
                            .content(post.getContent())
                            .tags(tagList)
                            .userAvatarUrl(avatar)
                            .mediaUrl(medias)
                            .commentCount(comments)
                            .likeCount(likes)
                            .build();
                });
    }

    public Mono<List<PostResponse>> getListPost(GetListPostRequest request) {
        return Flux.fromIterable(request.getPostIds())
                .flatMap(postId -> getPost(GetPostRequest.builder()
                        .postId(postId)
                        .mediaType(request.getMediaType())
                        .build()
                ))
                .collectList();
    }

    public Mono<Void> deletePost(String postId) {
        return ReactiveSecurityContextHolder.getContext()
                .map(securityContext -> securityContext.getAuthentication().getPrincipal())
                .map(principal -> ((Jwt) principal).getSubject())
                .flatMap(userId -> postRepository.deleteById(postId)
                        .then(likeRepository.deleteByUserIdAndTargetId(userId, postId))
                )
                .then(Mono.defer(() -> {
                    ProducerRecord<String, Object> producerRecord = new ProducerRecord<>("delete_post_command", postId);
                    SenderRecord<String, Object, String> senderRecord = SenderRecord.create(producerRecord, "delete post");

                    return kafkaSender.send(Mono.just(senderRecord))
                            .then();
                }));
    }

    public Mono<Void> updatePost(UpdatePostRequest request) {
        return postRepository.findById(request.getPostId())
                .flatMap(post -> {
                    post.setContent(request.getContent());

                    return postRepository.save(post)
                            .then(tagRepository.deleteByPostId(request.getPostId()));
                })
                .then(Mono.defer(() ->
                    Flux.fromIterable(request.getTags())
                            .flatMap(tag -> {
                                var t = Tag.builder()
                                        .id(UUID.randomUUID().toString())
                                        .postId(request.getPostId())
                                        .content(tag)
                                        .build();

                                return r2dbcEntityTemplate.insert(Tag.class).using(t);
                            })
                            .collectList()
                ))
                .then(Mono.defer(() -> {
                    var command = UpdatePostCommand.builder()
                            .postId(request.getPostId())
                            .content(request.getContent())
                            .tags(request.getTags())
                            .build();

                    ProducerRecord<String, Object> producerRecord = new ProducerRecord<>("update_post_command", command);
                    SenderRecord<String, Object, String> senderRecord = SenderRecord.create(producerRecord, "update post");

                    return kafkaSender.send(Mono.just(senderRecord))
                            .then();
                }));
    }

    // Cac thao tac voi like va comment

    public Flux<LikeResponse> getLikeList(String postId) {
        return likeRepository.findAllByTargetId(postId)
                .collectList()

                // Lay thong tin co ban cua nhung nguoi thich bai viet
                .flatMapMany(likes -> webClient.post()
                        .uri("http://localhost:8081/profile/get-like-info")
                        .bodyValue(UserBasicInfoRequest.builder()
                                .userId(likes.stream().map(Like::getUserId).toList())
                        )
                        .retrieve()
                        .bodyToFlux(UserInfoLikeResponse.class)

                        // Voi moi nguoi, lay avatar de hien thi
                        .flatMap(userInfoLikeResponse -> webClient.get()
                                .uri("http://localhost:8084/storage/get/avt-feed?ownerId=" + userInfoLikeResponse.getUserId())
                                .retrieve()
                                .bodyToMono(String.class)
                                // Map qua like Response
                                .map(avatarUrl -> LikeResponse.builder()
                                        .userId(userInfoLikeResponse.getUserId())
                                        .displayName(userInfoLikeResponse.getDisplayName())
                                        .userName(userInfoLikeResponse.getUsername())
                                        .avatarUrl(avatarUrl)
                                        .build()
                                )
                        )
                );
    }

    public Flux<CommentResponse> getComment(GetCommentRequest request) {
        return commentRepository.getCommentRequest(request.getPostId(), request.getLimit(), request.getCurrentIndex())
                .flatMap(comment ->
                        // Lay avatar cua nguoi binh luan
                        webClient.get()
                                .uri("http://localhost:8084/storage/get/avt-feed?ownerId=" + comment.getUserId())
                                .retrieve()
                                .bodyToMono(String.class)
                                .map(avt -> CommentResponse.builder()
                                        .commentId(comment.getId())
                                        .postOf(request.getPostOf())
                                        .actorId(comment.getUserId())
                                        .content(comment.getContent())
                                        .actorAvatar(avt)
                                        .build()
                                )
                        );
    }

    public Flux<CommentResponse> getChildComment(GetCommentRequest request) {
        return commentRepository.getChildComment(request.getPostId(), request.getGetChildOf(), request.getLimit(), request.getCurrentIndex())
                .flatMap(comment ->
                        // Lay avatar cua nguoi binh luan
                        webClient.get()
                                .uri("http://localhost:8084/storage/get/avt-feed?ownerId=" + comment.getUserId())
                                .retrieve()
                                .bodyToMono(String.class)
                                .map(avt -> CommentResponse.builder()
                                        .commentId(comment.getId())
                                        .postOf(request.getPostOf())
                                        .actorId(comment.getUserId())
                                        .content(comment.getContent())
                                        .actorAvatar(avt)
                                        .build()
                                )
                );
    }

    public Mono<Void> deleteComment(String commentId) {
        return commentRepository.deleteAllByParentId(commentId)
                .then(commentRepository.deleteById(commentId));
    }

    public Mono<Void> updateComment(String commentId, String content) {
        return commentRepository.findById(commentId)
                .flatMap(comment -> {
                    comment.setContent(content);
                    comment.setUpdateAt(Instant.now());

                    return commentRepository.save(comment)
                            .then();
                });
    }
}
