package com.dauducbach.post_service.service;

import com.dauducbach.event.story_upload.SaveStoryCommand;
import com.dauducbach.event.story_upload.SaveStorySuccessEvent;
import com.dauducbach.event.story_upload.StoryRollback;
import com.dauducbach.post_service.entity.Story;
import com.dauducbach.post_service.entity.StoryItem;
import com.dauducbach.post_service.repository.StoryItemRepository;
import com.dauducbach.post_service.repository.StoryRepository;
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

public class StoryUploadCommandService {
    R2dbcEntityTemplate r2dbcEntityTemplate;
    StoryItemRepository storyItemRepository;
    StoryRepository storyRepository;
    KafkaSender<String, Object> kafkaSender;

    @KafkaListener(topics = "save_story_command")
    public void saveStory(@Payload SaveStoryCommand command) {
        storyRepository.existsByUserId(command.userId())
                .flatMap(exists -> {
                    if (!exists) {
                        var story = Story.builder()
                                .id(UUID.randomUUID().toString())
                                .userId(command.userId())
                                .build();

                        var storyItem = StoryItem.builder()
                                .id(command.itemId())
                                .storyId(story.getId())
                                .mediaUrl(command.mediaUrl())
                                .audioUrl(command.audioUrl())
                                .createAt(Instant.now())
                                .isExpired(false)
                                .build();

                        return r2dbcEntityTemplate.insert(Story.class).using(story)
                                .then(r2dbcEntityTemplate.insert(StoryItem.class).using(storyItem));
                    } else {
                        return storyRepository.findByUserId(command.userId())
                                .flatMap(story -> {
                                    var storyItem = StoryItem.builder()
                                            .id(command.itemId())
                                            .storyId(story.getId())
                                            .mediaUrl(command.mediaUrl())
                                            .audioUrl(command.audioUrl())
                                            .createAt(Instant.now())
                                            .isExpired(false)
                                            .build();

                                    return r2dbcEntityTemplate.insert(StoryItem.class).using(storyItem);
                                });
                    }
                })
                .then(Mono.defer(() -> {
                    var event = new SaveStorySuccessEvent(command.itemId(), Instant.now());

                    ProducerRecord<String, Object> producerRecord = new ProducerRecord<>("save_story_success_event", event);
                    SenderRecord<String, Object, String> senderRecord = SenderRecord.create(producerRecord, "story creation");

                    return kafkaSender.send(Mono.just(senderRecord))
                            .then();
                }))
                .onErrorResume(err -> {
                    log.info("Error: {}", err.getMessage());

                    var rollback = new StoryRollback(command.itemId(), command.userId());

                    ProducerRecord<String, Object> producerRecord = new ProducerRecord<>("save_story_fail_event", rollback);
                    SenderRecord<String, Object, String> senderRecord = SenderRecord.create(producerRecord, "story creation");

                    return kafkaSender.send(Mono.just(senderRecord))
                            .then();
                })
                .subscribe();
    }

    @KafkaListener(topics = "rollback_story_post_service")
    public void rollback(@Payload StoryRollback storyRollback) {
        storyItemRepository.deleteById(storyRollback.itemId())
                .subscribe();
    }
}
