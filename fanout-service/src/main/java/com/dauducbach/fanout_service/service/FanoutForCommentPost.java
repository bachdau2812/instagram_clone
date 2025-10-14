package com.dauducbach.fanout_service.service;

import com.dauducbach.event.comment_post.*;
import com.dauducbach.fanout_service.dto.request.CommentRequest;
import com.dauducbach.fanout_service.dto.request.UserBasicInfoRequest;
import com.dauducbach.fanout_service.dto.response.UserInfo;
import com.dauducbach.fanout_service.entity.PostIndex;
import com.dauducbach.fanout_service.repository.PostIndexRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.KafkaListeners;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderRecord;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j

public class FanoutForCommentPost {
    KafkaSender<String, Object> kafkaSender;
    WebClient webClient;
    ReactiveRedisTemplate<String, CommentPostSuccessEvent> redisCommentInfo;
    PostIndexRepository postIndexRepository;

    public Mono<Void> commentPost(CommentRequest request) {
        log.info("1. Start comment saga");
        var command = new CommentPostCommand(
                request.getActorId(),
                request.getPostId(),
                request.getParentIdOf(),
                request.getParentId(),
                request.getParentIdOf(),
                request.getContent()
        );

        ProducerRecord<String, Object> producerRecord = new ProducerRecord<>("comment_post_command", request.getActorId(), command);
        SenderRecord<String, Object, String> senderRecord = SenderRecord.create(producerRecord, "comment post");

        return kafkaSender.send(Mono.just(senderRecord))
                .then();
    }

    @KafkaListener(topics = "comment_post_success_event")
    public void updatePostIndex(@Payload CommentPostSuccessEvent event) {
        log.info("2. Save comment in post service success");
        redisCommentInfo.opsForValue().set("comment_info:" + event.commentId(), event)
                .then(Mono.defer(() -> {
                    var command = new CommentPostIndexCommand(
                            event.commentId(),
                            event.postId(),
                            event.actorId()
                    );

                    ProducerRecord<String, Object> producerRecord = new ProducerRecord<>("comment_post_index_command", event.actorId(), command);
                    SenderRecord<String, Object, String> senderRecord = SenderRecord.create(producerRecord, "comment post");

                    return kafkaSender.send(Mono.just(senderRecord))
                            .then();
                }))
                .subscribe();
    }

    @KafkaListener(topics = "comment_post_index_success_event")
    public void updateFeed(@Payload CommentPostIndexSuccessEvent event) {
        log.info("3. Save actorId in post index success");
        redisCommentInfo.opsForValue().get("comment_info:" + event.commentId())
                .flatMap(comment -> {
                    var command = new CommentUpdateFeedCommand(
                            event.commentId(),
                            comment.actorId(),
                            comment.postId(),
                            comment.postOf(),
                            comment.parentIdOf()
                    );

                    ProducerRecord<String, Object> producerRecord = new ProducerRecord<>("comment_update_feed_command", event.actorId(), command);
                    SenderRecord<String, Object, String> senderRecord = SenderRecord.create(producerRecord, "comment post");

                    return kafkaSender.send(Mono.just(senderRecord))
                            .then();
                })
                .subscribe();
    }

    @KafkaListener(topics = "comment_update_feed_success_event")
    public void commentBroadcast(@Payload CommentUpdateFeedSuccessEvent event) {
        log.info("4. Comment update feed success");
        redisCommentInfo.opsForValue().get("comment_info:" + event.commentId())
                .flatMap(comment -> {
                    var command = new CommentBroadcastCommand(
                            comment.commentId(),
                            comment.actorId(),
                            comment.postId(),
                            comment.postOf(),
                            comment.parentId(),
                            comment.parentIdOf(),
                            comment.content()
                    );

                    ProducerRecord<String, Object> producerRecord = new ProducerRecord<>("comment_broadcast_command", comment.actorId(), command);
                    SenderRecord<String, Object, String> senderRecord = SenderRecord.create(producerRecord, "comment post");

                    return kafkaSender.send(Mono.just(senderRecord))
                            .then();
                })
                .subscribe();
    }

    @KafkaListener(topics = "comment_broadcast_success_event")
    public void sendNotification(@Payload CommentBroadSuccessEvent event) {
        log.info("5. Comment broadcast success");
        redisCommentInfo.opsForValue().get("comment_info:" + event.commentId())
                .flatMap(comment -> postIndexRepository.findById(comment.postId())      // Lay danh sach nhung nguoi da comment bai viet va gui thong bao
                        .map(PostIndex::getCommentedId)
                        .flatMap(commentedIds -> {
                            log.info("Comment info: {}", comment);
                            // Xay dung request de lay thong tin user cho viec gui thong bao
                            UserBasicInfoRequest userBasicInfoRequest;
                            if (comment.parentIdOf() != null) {
                                userBasicInfoRequest = UserBasicInfoRequest.builder()
                                        .userId(List.of(comment.actorId(), comment.parentIdOf(), comment.postOf()))
                                        .build();
                            } else {
                                userBasicInfoRequest = UserBasicInfoRequest.builder()
                                        .userId(List.of(comment.actorId(), comment.postOf()))
                                        .build();
                            }


                            commentedIds.forEach(id -> {
                                userBasicInfoRequest.getUserId().add(id);
                            });

                            // Lay thong tin cua cac recipient
                            return Mono.zip(
                                    webClient.get()
                                            .uri("http://localhost:8084/storage/get/avt-feed?ownerId=" + comment.actorId())
                                            .retrieve()
                                            .bodyToMono(String.class),
                                    webClient.post()
                                            .uri("http://localhost:8081/profile/get-basic-info")
                                            .bodyValue(userBasicInfoRequest)
                                            .retrieve()
                                            .bodyToMono(new ParameterizedTypeReference<List<UserInfo>>() {})
                            );
                        })
                        .flatMap(tuple -> {
                            List<UserInfo> commentedInfo = tuple.getT2();
                            String actorAvatarUrl = tuple.getT1();

                            UserInfo actorInfo = commentedInfo.stream()
                                    .filter(info -> info.getUserId().equals(comment.actorId()))
                                    .findFirst()
                                    .orElse(null);

                            UserInfo targetInfo = commentedInfo.stream()
                                    .filter(info -> info.getUserId().equals(comment.parentIdOf()))
                                    .findFirst()
                                    .orElse(null);

                            UserInfo postOwnerInfo = commentedInfo.stream()
                                    .filter(info -> info.getUserId().equals(comment.postOf()))
                                    .findFirst()
                                    .orElse(null);

                            List<UserInfo> recipientInfo = commentedInfo.stream().filter(info -> info.getUserId().equals(comment.actorId())).toList();

                            var command = new NotificationCommentPostCommand(
                                    comment.commentId(),
                                    actorInfo,
                                    targetInfo,
                                    postOwnerInfo,
                                    recipientInfo,
                                    actorAvatarUrl,
                                    Map.of(),
                                    comment.content()
                            );

                            ProducerRecord<String, Object> producerRecord = new ProducerRecord<>("notification_comment_post_command", comment.actorId(), command);
                            SenderRecord<String, Object, String> senderRecord = SenderRecord.create(producerRecord, "comment post");

                            return kafkaSender.send(Mono.just(senderRecord))
                                    .then();
                        })
                )
                .subscribe();
    }

    @KafkaListener(topics = "notification_comment_post_success_event")
    public void commentSuccess(@Payload NotificationCommentPostSuccessEvent event) {
        redisCommentInfo.opsForValue().get("comment_info:" + event.commentId())
                .doOnSuccess(comment -> log.info("Comment success: {}", comment))
                .then(redisCommentInfo.opsForValue().delete("comment_info:" + event.commentId()))
                .doOnSuccess(aBoolean -> log.info("Delete comment from redis"))
                .subscribe();
    }

    // rollback
    public Mono<Void> rollbackCommentPost(String commentId) {
        log.info("1. Rollback post service");
        ProducerRecord<String, Object> producerRecord = new ProducerRecord<>("rollback_comment_post", commentId);
        SenderRecord<String, Object, String> senderRecord = SenderRecord.create(producerRecord, "comment post");

        return kafkaSender.send(Mono.just(senderRecord))
                .then();
    }

    @KafkaListeners({
            @KafkaListener(topics = "comment_post_index_fail_event"),
            @KafkaListener(topics = "comment_update_feed_fail_event"),
            @KafkaListener(topics = "notification_comment_post_fail_event"),
            @KafkaListener(topics = "comment_broadcast_fail_event")
    })
    public void rollback(@Payload CommentRollback rollback) {
        rollbackCommentPost(rollback.commentId())
                .subscribe();
    }
}
