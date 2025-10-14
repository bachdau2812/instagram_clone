package com.dauducbach.fanout_service.service;

import com.dauducbach.event.like_post.*;
import com.dauducbach.event.post_creation.NotificationPostCommand;
import com.dauducbach.fanout_service.dto.request.LikePostRequest;
import com.dauducbach.fanout_service.dto.request.UserBasicInfoRequest;
import com.dauducbach.fanout_service.dto.response.UserInfo;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.core.ParameterizedTypeReference;
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

public class FanoutForLikePost {
    WebClient webClient;
    KafkaSender<String, Object> kafkaSender;

    // Start saga ( save in post service)
    public Mono<Void> likePost(LikePostRequest request) {
        log.info("1. Start saga");
        var command = LikePostCommand.builder()
                .actorId(request.getActorId())
                .targetId(request.getTargetId())
                .postId(request.getPostId())
                .build();

        ProducerRecord<String, Object> producerRecord = new ProducerRecord<>("like_post_command", command);
        SenderRecord<String, Object, String> senderRecord = SenderRecord.create(producerRecord, "post operation");

        return kafkaSender.send(Mono.just(senderRecord))
                .then();
    }

    // update post index in elasticsearch

    @KafkaListener(topics = "like_post_success_event")
    public void updatePostIndex(@Payload LikePostSuccessEvent event) {
        log.info("2. Save in post service success");
        var command = LikePostIndexCommand.builder()
                .actorId(event.getActorId())
                .targetId(event.getTargetId())
                .postId(event.getPostId())
                .build();

        ProducerRecord<String, Object> producerRecord = new ProducerRecord<>("like_post_index_command", command);
        SenderRecord<String, Object, String> senderRecord = SenderRecord.create(producerRecord, "post operation");

       kafkaSender.send(Mono.just(senderRecord))
                .subscribe();
    }

    @KafkaListener(topics = "like_post_index_success_event")
    public void updateFeed(@Payload LikePostIndexSuccessEvent event) {
        log.info("3. Update post index complete");
        var command = UpdateFeedCommand.builder()
                .actorId(event.getActorId())
                .targetId(event.getTargetId())
                .postId(event.getPostId())
                .build();

        ProducerRecord<String, Object> producerRecord = new ProducerRecord<>("update_feed_command", command);
        SenderRecord<String, Object, String> senderRecord = SenderRecord.create(producerRecord, "post operation");

        kafkaSender.send(Mono.just(senderRecord))
                .subscribe();
    }

    @KafkaListener(topics = "update_feed_success_event")
    public void notification(@Payload UpdateFeedSuccessEvent event) {
        log.info("4. Update feed success: {}", event);
        sendNotification(event.getActorId(), event.getPostId(), event.getTargetId())
                .subscribe();
    }

    @KafkaListener(topics = "notification_like_success_event")
    public void endSaga(@Payload NotificationLikeSuccessEvent event) {
        log.info("Saga success: {}", event);
    }

    private Mono<Void> sendNotification(String actorId, String postId, String targetId) {
        Mono<UserInfo> sourceInfo = webClient.post()
                .uri("http://localhost:8081/profile/get-basic-info")
                .bodyValue(UserBasicInfoRequest.builder()
                        .userId(List.of(actorId))
                        .build()
                )
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<UserInfo>>() {
                })
                .map(List::getFirst);

        Mono<UserInfo> targetInfo = webClient.post()
                .uri("http://localhost:8081/profile/get-basic-info")
                .bodyValue(UserBasicInfoRequest.builder()
                        .userId(List.of(targetId))
                        .build()
                )
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<UserInfo>>() {})
                .map(userInfos -> userInfos.get(0));

        Mono<String> sourceAvatarUrl = webClient.get()
                .uri("http://localhost:8084/storage/get/avt-feed?ownerId=" + actorId)
                .retrieve()
                .bodyToMono(String.class);

        return Mono.zip(
                sourceInfo,
                targetInfo,
                sourceAvatarUrl
        ).flatMap(tuples -> {
                    UserInfo sInfo = tuples.getT1();
                    UserInfo tInfo = tuples.getT2();
                    String sAvtUrl = tuples.getT3();

                    var command = NotificationLikeCommand.builder()
                            .actorInfo(sInfo)
                            .targetInfo(tInfo)
                            .actorAvatarUrl(sAvtUrl)
                            .data(Map.of())
                            .postId(postId)
                            .build();

                    ProducerRecord<String, Object> producerRecord = new ProducerRecord<>("notification_like_command", command);
                    SenderRecord<String, Object, String> senderRecord = SenderRecord.create(producerRecord, "profile operator");

                    return kafkaSender.send(Mono.just(senderRecord))
                            .then();
                }
        );
    }

    // rollback
    public Mono<Void> rollbackLikePost(String actorId, String postId) {
        var likeRollback = LikeRollback.builder()
                .actorId(actorId)
                .postId(postId)
                .build();

        ProducerRecord<String, Object> producerRecord = new ProducerRecord<>("rollback_like_post", likeRollback);
        SenderRecord<String, Object, String> senderRecord = SenderRecord.create(producerRecord, "profile operator");

        return kafkaSender.send(Mono.just(senderRecord))
                .then();
    }

    public Mono<Void> rollbackLikePostIndex(String actorId, String postId) {
        var likeRollback = LikeRollback.builder()
                .actorId(actorId)
                .postId(postId)
                .build();

        ProducerRecord<String, Object> producerRecord = new ProducerRecord<>("rollback_like_post_index", likeRollback);
        SenderRecord<String, Object, String> senderRecord = SenderRecord.create(producerRecord, "profile operator");

        return kafkaSender.send(Mono.just(senderRecord))
                .then();
    }

    public Mono<Void> rollbackUpdateFeed(String actorId, String postId) {
        var likeRollback = LikeRollback.builder()
                .actorId(actorId)
                .postId(postId)
                .build();

        ProducerRecord<String, Object> producerRecord = new ProducerRecord<>("rollback_update_feed", likeRollback);
        SenderRecord<String, Object, String> senderRecord = SenderRecord.create(producerRecord, "profile operator");

        return kafkaSender.send(Mono.just(senderRecord))
                .then();
    }

    @KafkaListeners({
            @KafkaListener(topics = "like_post_fail_event"),
            @KafkaListener(topics = "like_post_index_fail_event"),
            @KafkaListener(topics = "update_feed_fail_event"),
            @KafkaListener(topics = "notification_like_fail_event")
    })
    public void handleFailEvent(ConsumerRecord<String, LikeRollback> record) {

        rollback(record.topic(), record.value().getActorId(), record.value().getPostId())
                .subscribe();
    }

    public Mono<Void> rollback(String event, String actorId, String postId) {
        return switch (event) {
            case "like_post_fail_event" -> Mono.error(new RuntimeException("Save post fail"));
            case "like_post_index_fail_event" -> rollbackLikePost(actorId, postId);
            case "update_feed_fail_event" ->
                rollbackLikePost(actorId, postId)
                        .then(rollbackLikePostIndex(actorId, postId));
            case "notification_like_fail_event" ->
                rollbackLikePost(actorId, postId)
                        .then(rollbackLikePostIndex(actorId, postId))
                        .then(rollbackUpdateFeed(actorId, postId));

            default -> Mono.empty();
        };
    }
}
