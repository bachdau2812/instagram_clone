package com.dauducbach.feed_service.service;

import com.dauducbach.event.story_upload.CacheStoryCommand;
import com.dauducbach.event.story_upload.CacheStorySuccessEvent;
import com.dauducbach.event.story_upload.StoryRollback;
import com.dauducbach.feed_service.dto.response.StoryInfo;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderRecord;

import java.time.Duration;
import java.time.Instant;

@Service
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RequiredArgsConstructor
@Slf4j

public class UpdateFeedCacheStoryUpload {
    KafkaSender<String, Object> kafkaSender;
    ReactiveRedisTemplate<String, String> redisTemplate;
    ReactiveRedisTemplate<String, StoryInfo> cacheStory;

    @KafkaListener(topics = "cache_story_command")
    public void cacheStory(@Payload CacheStoryCommand command) {
        log.info("Cache for story: {}", command);
        var storyInfo = StoryInfo.builder()
                .itemId(command.itemId())
                .userId(command.userId())
                .mediaUrl(command.mediaUrl())
                .audioUrl(command.audioUrl())
                .createAt(command.createAt())
                .build();

        redisTemplate.opsForZSet().add("user_story:" + command.userId(), command.itemId(), command.createAt())
                .then(cacheStory.opsForValue().set("story:" + command.itemId(), storyInfo))
                .flatMap(aBoolean -> {
                    var event = new CacheStorySuccessEvent(
                            command.itemId()
                    );

                    ProducerRecord<String, Object> producerRecord = new ProducerRecord<>("cache_story_success_event", event);
                    SenderRecord<String, Object, String> senderRecord = SenderRecord.create(producerRecord, "story creation");

                    return kafkaSender.send(Mono.just(senderRecord))
                            .then();
                })
                .onErrorResume(err -> {
                    log.info("Error: {}", err.getMessage());

                    var rollback = new StoryRollback(command.itemId(), command.userId());

                    ProducerRecord<String, Object> producerRecord = new ProducerRecord<>("cache_story_fail_event", rollback);
                    SenderRecord<String, Object, String> senderRecord = SenderRecord.create(producerRecord, "story creation");

                    return kafkaSender.send(Mono.just(senderRecord))
                            .then();
                })
                .subscribe();
    }

    // rollback
    @KafkaListener(topics = "rollback_story_feed_service")
    public void rollback(@Payload StoryRollback storyRollback) {
        redisTemplate.opsForZSet().remove("user_story:" + storyRollback.userId(), storyRollback.itemId())
                .then(cacheStory.opsForValue().delete("story:" + storyRollback.itemId()))
                .subscribe();
    }
}
