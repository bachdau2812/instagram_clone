package com.dauducbach.feed_service.service;

import com.dauducbach.event.post_creation.CacheFeedCommand;
import com.dauducbach.event.post_creation.CacheFeedSuccessEvent;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderRecord;

import java.time.Duration;
import java.util.List;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j

public class PostCreateFanout {
    ReactiveRedisTemplate<String, Object> redisTemplate;
    KafkaSender<String, Object> kafkaSender;

    @KafkaListener(topics = "cache_feed_command")
    public void cacheFeed(@Payload CacheFeedCommand command) {
        log.info("In Cache Feed");
        Flux.fromIterable(command.getFanoutTo())
                .flatMap(userId ->
                        redisTemplate.opsForZSet()
                                .add("feed:" + userId, command.getPostId(), command.getScore())
                )
                .collectList()
                .then(Mono.defer(() -> {
                    // success event
                    var event = CacheFeedSuccessEvent.builder()
                            .postId(command.getPostId())
                            .userId(command.getUserId())
                            .followerList(command.getFanoutTo())
                            .build();

                    ProducerRecord<String, Object> producerRecord = new ProducerRecord<>("cache_feed_success_event", event);
                    SenderRecord<String, Object, String> senderRecord = SenderRecord.create(producerRecord, "post creation");

                    return kafkaSender.send(Mono.just(senderRecord))
                            .then(redisTemplate.opsForValue().set("for_rollback:" + command.getPostId(), command.getFanoutTo(), Duration.ofMinutes(2)))
                            .doOnSuccess(aBoolean -> log.info("Save feed complete"))
                            .then();
                }))
                .onErrorResume(err -> {
                    // fail event
                    log.info("Error: {}", err.getMessage());
                    ProducerRecord<String, Object> producerRecord =
                            new ProducerRecord<>("cache_feed_fail_event", command.getPostId());
                    SenderRecord<String, Object, String> senderRecord =
                            SenderRecord.create(producerRecord, "post creation");

                    return kafkaSender.send(Mono.just(senderRecord)).then();
                })
                .subscribe();
    }

    @KafkaListener(topics = "rollback_feed_command")
    public void rollback(@Payload String postId) {
        redisTemplate.opsForValue()
                .get("for_rollback:" + postId)
                .cast(List.class) // cast về List
                .flatMapMany(userIds -> Flux.fromIterable((List<String>) userIds))
                .flatMap(userId -> redisTemplate.opsForZSet().remove("feed:" + userId, postId))
                .then(redisTemplate.opsForValue().delete("for_rollback:" + postId))
                .subscribe();
    }
}
