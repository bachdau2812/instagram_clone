package com.dauducbach.post_service.service;

import com.dauducbach.event.like_post.LikePostCommand;
import com.dauducbach.event.like_post.LikePostSuccessEvent;
import com.dauducbach.event.like_post.LikeRollback;
import com.dauducbach.post_service.entity.Like;
import com.dauducbach.post_service.repository.LikeRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderRecord;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j

public class LikePostCommandService {
    R2dbcEntityTemplate r2dbcEntityTemplate;
    LikeRepository likeRepository;
    KafkaSender<String, Object> kafkaSender;

    @KafkaListener(topics = "like_post_command")
    public void likePost(@Payload LikePostCommand command) {

        likeRepository.existsByUserIdAndTargetId(command.getActorId(), command.getPostId())
                .flatMap(existed -> {
                    if (!existed) {
                        var like = Like.builder()
                                .id(UUID.randomUUID().toString())
                                .targetId(command.getPostId())
                                .userId(command.getActorId())
                                .timestamp(Instant.now())
                                .build();

                        return r2dbcEntityTemplate.insert(Like.class).using(like)
                                .flatMap(res -> {
                                    var event = LikePostSuccessEvent.builder()
                                            .actorId(command.getActorId())
                                            .postId(command.getPostId())
                                            .targetId(command.getTargetId())
                                            .build();
                                    ProducerRecord<String, Object> producerRecord = new ProducerRecord<>("like_post_success_event", event);
                                    SenderRecord<String, Object, String> senderRecord = SenderRecord.create(producerRecord, "Like post");

                                    return kafkaSender.send(Mono.just(senderRecord))
                                            .then();
                                })
                                .onErrorResume(err -> {
                                    log.info("Error like post: {}", err.getMessage());

                                    var likeRollback = LikeRollback.builder()
                                            .actorId(command.getActorId())
                                            .postId(command.getActorId())
                                            .build();

                                    ProducerRecord<String, Object> producerRecord = new ProducerRecord<>("like_post_fail_event", likeRollback);
                                    SenderRecord<String, Object, String> senderRecord = SenderRecord.create(producerRecord, "Like post");

                                    return kafkaSender.send(Mono.just(senderRecord))
                                            .then();
                                });
                    } else {
                        return likeRepository.deleteByUserIdAndTargetId(command.getActorId(), command.getPostId())
                                .then(Mono.defer(() -> {
                                    var event = LikePostSuccessEvent.builder()
                                            .actorId(command.getActorId())
                                            .postId(command.getPostId())
                                            .targetId(command.getTargetId())
                                            .build();
                                    ProducerRecord<String, Object> producerRecord = new ProducerRecord<>("like_post_success_event", event);
                                    SenderRecord<String, Object, String> senderRecord = SenderRecord.create(producerRecord, "Like post");

                                    return kafkaSender.send(Mono.just(senderRecord))
                                            .then();
                                }))
                                .onErrorResume(err -> {
                                    log.info("Error unlike post: {}", err.getMessage());

                                    return Mono.empty();
                                });
                    }
                })
                .subscribe();

    }

    @KafkaListener(topics = "rollback_like_post")
    public void rollback(@Payload LikeRollback likeRollback) {
        likeRepository.deleteByUserIdAndTargetId(likeRollback.getActorId(), likeRollback.getPostId())
                .subscribe();
    }

}
