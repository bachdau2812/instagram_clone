package com.dauducbach.storage_service.service;

import com.dauducbach.event.story_upload.StoryRollback;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j

public class RollbackPostMedia {
    private final UploadFileService uploadFileService;

    @KafkaListener(topics = "rollback_media_post_command")
    public void rollback(@Payload String postId) {
        uploadFileService.deleteByOwnerId(postId)
                .doOnSuccess(unused -> log.info("Delete for {}", postId))
                .subscribe();
    }

    @KafkaListener(topics = "rollback_story_storage_service")
    public void rollback(@Payload StoryRollback storyRollback) {
        uploadFileService.deleteByOwnerId(storyRollback.itemId())
                .doOnSuccess(unused -> log.info("Delete for: {}", storyRollback.itemId()))
                .subscribe();
    }
}
