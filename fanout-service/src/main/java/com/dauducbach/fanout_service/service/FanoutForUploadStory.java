package com.dauducbach.fanout_service.service;

import com.dauducbach.event.story_upload.*;
import com.dauducbach.fanout_service.dto.request.StoryUploadRequest;
import com.dauducbach.fanout_service.dto.request.UserBasicInfoRequest;
import com.dauducbach.fanout_service.dto.response.UserInfo;
import com.dauducbach.fanout_service.repository.ProfileIndexRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.KafkaListeners;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderRecord;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j

public class FanoutForUploadStory {
    KafkaSender<String, Object> kafkaSender;
    WebClient webClient;
    ReactiveRedisTemplate<String, StoryInfo> redisTemplate;
    ProfileIndexRepository profileIndexRepository;

    // Start saga
    public Mono<Void> upStory(StoryUploadRequest request) {
        log.info("1. Upload media and save in post service");
        String itemId = UUID.randomUUID().toString();

        // Upload media
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
            builder.asyncPart("files", request.getMedia().content(), DataBuffer.class)
                    .header("Content-Disposition",
                            "form-data; name=files; filename=" + request.getMedia().filename())
                    .contentType(Objects.requireNonNull(request.getMedia().headers().getContentType()));
        builder.part("ownerId", itemId);
        builder.part("isAvatar", "false");

        return ReactiveSecurityContextHolder.getContext()
                .map(securityContext -> ((Jwt)securityContext.getAuthentication().getPrincipal()).getTokenValue())
                .flatMap(token -> {
                    Mono<String> mediaUrl = webClient.post()
                            .uri("http://localhost:8084/storage/upload-media")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.MULTIPART_FORM_DATA)
                            .body(BodyInserters.fromValue(builder.build()))
                            .retrieve()
                            .bodyToFlux(String.class)
                            .collectList()
                            .map(strings -> strings.get(0));

                    Mono<String> audioUrl = webClient.get()
                            .uri("http://localhost:8084/storage/get/audio?displayName=" + request.getAudioDisplayName())
                            .retrieve()
                            .bodyToMono(String.class);

                    return Mono.zip(
                            mediaUrl,
                            audioUrl
                    );
                })
                .flatMap(medias -> {
                    String mediaUrl = medias.getT1();
                    String audioUrl = medias.getT2();

                    log.info("1.1 Media url: {}, audioUrl: {}", mediaUrl, audioUrl);
                    var command = new SaveStoryCommand(
                            itemId,
                            request.getUserId(),
                            mediaUrl,
                            audioUrl
                    );

                    ProducerRecord<String, Object> producerRecord = new ProducerRecord<>("save_story_command", command);
                    SenderRecord<String, Object, String> senderRecord = SenderRecord.create(producerRecord, "story creation");

                    var storyInfo = StoryInfo.builder()
                            .itemId(itemId)
                            .userId(request.getUserId())
                            .mediaUrl(mediaUrl)
                            .audioUrl(audioUrl)
                            .build();

                    return kafkaSender.send(Mono.just(senderRecord))
                            .then(redisTemplate.opsForValue().set("story_item:" + itemId, storyInfo))
                            .then();
                });
    }

    // cache feed
    @KafkaListener(topics = "save_story_success_event")
    public void cacheStory(@Payload SaveStorySuccessEvent event) {
        log.info("2. Save in post service success: {}", event);
        redisTemplate.opsForValue()
                .get("story_item:" + event.itemId())
                .flatMap(storyInfo -> {
                    storyInfo.setCreateAt(event.createAt().toEpochMilli());
                    log.info("Story:{}", storyInfo);
                    var command = new CacheStoryCommand(
                            event.itemId(),
                            storyInfo.getUserId(),
                            storyInfo.getMediaUrl(),
                            storyInfo.getAudioUrl(),
                            event.createAt().toEpochMilli()
                    );

                    ProducerRecord<String, Object> producerRecord = new ProducerRecord<>("cache_story_command", command);
                    SenderRecord<String, Object, String> senderRecord = SenderRecord.create(producerRecord, "story creation");

                    return kafkaSender.send(Mono.just(senderRecord))
                            .then(redisTemplate.opsForValue().set("story_item:" + event.itemId(), storyInfo))
                            .then();
                })
                .subscribe();
    }

    // send notification
    @KafkaListener(topics = "cache_story_success_event")
    public void sendNotification(@Payload CacheStorySuccessEvent event) {
        log.info("3. Cache complete: {}", event);
        redisTemplate.opsForValue().get("story_item:" + event.itemId())
                .flatMap(storyInfo -> profileIndexRepository.findById(storyInfo.getUserId())
                        .flatMap(profileIndex -> {
                            List<String> userRequest = new ArrayList<>(profileIndex.getFollowerList());
                            userRequest.add(storyInfo.getUserId());

                            Mono<String> actorAvt = webClient.get()
                                    .uri("http://localhost:8084/storage/get/avt-feed?ownerId=" + storyInfo.getUserId())
                                    .retrieve()
                                    .bodyToMono(String.class);

                            Mono<List<UserInfo>> userInfos = webClient.post()
                                    .uri("http://localhost:8081/profile/get-basic-info")
                                    .bodyValue(UserBasicInfoRequest.builder()
                                            .userId(userRequest)
                                            .build()
                                    )
                                    .retrieve()
                                    .bodyToMono(new ParameterizedTypeReference<List<UserInfo>>() {});

                            return Mono.zip(
                                    actorAvt,
                                    userInfos
                            );
                        })
                        .flatMap(tuple -> {
                            String avt = tuple.getT1();
                            List<UserInfo> userInfos = tuple.getT2();

                            var actorInfo = userInfos.stream().filter(s -> s.getUserId().equals(storyInfo.getUserId())).toList().get(0);
                            var recipientList = userInfos.stream().filter(s -> !s.getUserId().equals(storyInfo.getUserId())).toList();
                            var command = new NotificationStoryCommand(
                                    event.itemId(),
                                    actorInfo,
                                    avt,
                                    recipientList
                            );

                            ProducerRecord<String, Object> producerRecord = new ProducerRecord<>("notification_story_command", command);
                            SenderRecord<String, Object, String> senderRecord = SenderRecord.create(producerRecord, "story creation");

                            return kafkaSender.send(Mono.just(senderRecord))
                                    .then();
                        })
                )
                .subscribe();
    }

    @KafkaListener(topics = "notification_story_success_event")
    public void upStorySuccess(@Payload NotificationStorySuccessEvent event) {
        log.info("END SAGA: {}", event);
        String key = "story_item:" + event.itemId();

        redisTemplate.opsForValue()
                .get(key)
                .doOnSuccess(storyInfo -> log.info("4. Up story success: {}", storyInfo))
                .then(redisTemplate.opsForValue().delete(key))
                .doOnSuccess(aBoolean -> log.info("5. Delete key: {}", key))
                .subscribe();
    }

    // rollback
    public Mono<Void> rollbackStorageService(StoryRollback storyRollback) {
        ProducerRecord<String, Object> producerRecord = new ProducerRecord<>("rollback_story_storage_service", storyRollback);
        SenderRecord<String, Object, String> senderRecord = SenderRecord.create(producerRecord, "story creation");

        return kafkaSender.send(Mono.just(senderRecord))
                .then();
    }

    public Mono<Void> rollbackSaveStory(StoryRollback storyRollback) {
        ProducerRecord<String, Object> producerRecord = new ProducerRecord<>("rollback_story_post_service", storyRollback);
        SenderRecord<String, Object, String> senderRecord = SenderRecord.create(producerRecord, "story creation");

        return kafkaSender.send(Mono.just(senderRecord))
                .then();
    }

    public Mono<Void> rollbackCache(StoryRollback storyRollback) {
        ProducerRecord<String, Object> producerRecord = new ProducerRecord<>("rollback_story_feed_service", storyRollback);
        SenderRecord<String, Object, String> senderRecord = SenderRecord.create(producerRecord, "story creation");

        return kafkaSender.send(Mono.just(senderRecord))
                .then();
    }

    @KafkaListeners({
            @KafkaListener(topics = "cache_story_fail_event"),
            @KafkaListener(topics = "notification_story_fail_event"),
            @KafkaListener(topics = "save_story_fail_event")
    })
    public void rollback(ConsumerRecord<String, StoryRollback> record) {
        handleError(record.topic(), record.value())
                .subscribe();
    }

    public Mono<Void> handleError(String topic, StoryRollback storyRollback) {
        return switch (topic) {
            case "save_story_fail_event" -> rollbackStorageService(storyRollback);
            case "cache_story_fail_event" -> rollbackStorageService(storyRollback)
                    .then(rollbackSaveStory(storyRollback));
            case "notification_story_fail_event" -> rollbackStorageService(storyRollback)
                    .then(rollbackSaveStory(storyRollback))
                    .then(rollbackCache(storyRollback));
            default -> Mono.empty();
        };
    }
}
