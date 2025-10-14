package com.dauducbach.fanout_service.service;

import com.dauducbach.event.post_creation.*;
import com.dauducbach.fanout_service.dto.request.PostCreationRequest;
import com.dauducbach.fanout_service.dto.request.UserBasicInfoRequest;
import com.dauducbach.fanout_service.dto.response.PostResponse;
import com.dauducbach.fanout_service.dto.response.UserInfo;
import com.dauducbach.fanout_service.repository.ProfileIndexRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.http.codec.multipart.FilePart;
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

import java.util.*;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j

public class FanoutPostCreation {
    WebClient webClient;
    KafkaSender<String, Object> kafkaSender;
    ProfileIndexRepository profileIndexRepository;
    ReactiveRedisTemplate<String, PostResponse> redisPostResponse;
    ObjectMapper objectMapper;

    // Start SAGA
    public Mono<Void> createPost(PostCreationRequest request) {
        // Send request save media and send event to PostService
        String postId = UUID.randomUUID().toString();
        var command = SavePostCommand.builder()
                .postId(postId)
                .userId(request.getUserId())
                .tags(request.getTags())
                .content(request.getContent())
                .build();

        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        for (FilePart file : request.getMedia()) {
            builder.asyncPart("files", file.content(), DataBuffer.class)
                    .header("Content-Disposition",
                            "form-data; name=files; filename=" + file.filename())
                    .contentType(Objects.requireNonNull(file.headers().getContentType()));
        }
        builder.part("ownerId", postId);
        builder.part("isAvatar", "false");

        return ReactiveSecurityContextHolder.getContext()
                .map(context -> ((Jwt) context.getAuthentication().getPrincipal()).getTokenValue())
                .flatMap(token ->
                        webClient.post()
                                .uri("http://localhost:8084/storage/upload-media")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.MULTIPART_FORM_DATA)
                                .body(BodyInserters.fromValue(builder.build()))
                                .retrieve()
                                .bodyToFlux(String.class)
                                .collectList()
                )
                .flatMap(strings -> {
                    var postResponse = PostResponse.builder()
                            .postId(command.getPostId())
                            .userId(command.getUserId())
                            .mediaUrl(strings)
                            .content(command.getContent())
                            .tags(command.getTags())
                            .build();

                    ProducerRecord<String, Object> producerRecord = new ProducerRecord<>("save_post_command", command);
                    SenderRecord<String, Object, String> senderRecord = SenderRecord.create(producerRecord, "post creation");

                    return kafkaSender.send(Mono.just(senderRecord))
                            .then(redisPostResponse.opsForValue().set("p_response:" + postResponse.getPostId(), postResponse))
                            .then();
                })
                .onErrorResume(err -> rollbackStorageService(command.getPostId())
                        .then(Mono.error(new RuntimeException("Error: {}"+ err.getMessage()))));
    }

    // Save Media
    @KafkaListener(topics = "save_post_success_event", groupId = "orchestrator")
    public void saveMediaPost(@Payload SavePostSuccessEvent event) {
        log.info("Save post in post service success");
        var command = SavePostIndexCommand.builder()
                .postId(event.getPostId())
                .userId(event.getUserId())
                .content(event.getContent())
                .tags(event.getTags())
                .createAt(event.getCreateAt().toEpochMilli())
                .updateAt(event.getUpdateAt().toEpochMilli())
                .build();

        ProducerRecord<String, Object> producerRecord = new ProducerRecord<>("save_post_index_command", command);
        SenderRecord<String, Object, String> senderRecord = SenderRecord.create(producerRecord, "post creation");

        kafkaSender.send(Mono.just(senderRecord))
                .subscribe();
    }

    // Cache Feed
    @KafkaListener(topics = "save_post_index_success_event", groupId = "orchestrator")
    public void cacheFeed(@Payload SavePostIndexSuccessEvent event) {
        log.info("Save post index success");
        profileIndexRepository.findById(event.getUserId())
                .flatMap(profileIndex -> {
                    if (profileIndex.getFollowerList().size() > 100) {
                        log.info(" > 100 :{}", profileIndex.getFollowerList().size());
                        return sendNotification(event.getUserId(), event.getPostId(), profileIndex.getFollowerList());
                    } else {
                        log.info(" < 100 ");
                        var command = CacheFeedCommand.builder()
                                .postId(event.getPostId())
                                .userId(event.getUserId())
                                .fanoutTo(profileIndex.getFollowerList())
                                .score(event.getCreateAt())
                                .build();

                        ProducerRecord<String, Object> producerRecord = new ProducerRecord<>("cache_feed_command", command);
                        SenderRecord<String, Object, String> senderRecord = SenderRecord.create(producerRecord, "post creation");

                        return kafkaSender.send(Mono.just(senderRecord))
                                .then();
                    }
                }).subscribe();
    }


    // send notification
    @KafkaListener(topics = "cache_feed_success_event", groupId = "orchestrator")
    public void sendNotification(@Payload CacheFeedSuccessEvent event) {
        log.info("Cache feed success");
        sendNotification(event.getUserId(), event.getPostId(), event.getFollowerList())
                .subscribe();
    }

    // end saga
    @KafkaListener(topics = "notification_post_success_event")
    public void responseToClient(@Payload NotificationPostSuccessEvent event) {
        log.info("Notification success: {}", event);

        String key = "p_response:" + event.getPostId();

        redisPostResponse.opsForValue()
                .get(key)
                .flatMap(postResponse -> {
                    log.info("Create post: {}", postResponse);
                    return redisPostResponse.opsForValue().delete(key);
                })
                .doOnSuccess(deleted -> {
                    if (Boolean.TRUE.equals(deleted)) {
                        log.info("Deleted key: {}", key);
                    } else {
                        log.warn("Key not deleted or not found: {}", key);
                    }
                })
                .doOnError(e -> log.error("Error handling Redis key: {}", key, e))
                .subscribe();
    }

    private Mono<Void> sendNotification(String userId, String postId, List<String> followerList) {
        Mono<UserInfo> sourceInfo = webClient.post()
                .uri("http://localhost:8081/profile/get-basic-info")
                .bodyValue(UserBasicInfoRequest.builder()
                        .userId(List.of(userId))
                        .build()
                )
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<UserInfo>>() {
                })
                .map(List::getFirst);

        Mono<List<UserInfo>> targetInfo = webClient.post()
                .uri("http://localhost:8081/profile/get-basic-info")
                .bodyValue(UserBasicInfoRequest.builder()
                        .userId(followerList)
                        .build()
                )
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<UserInfo>>() {
                });

        Mono<String> sourceAvatarUrl = webClient.get()
                .uri("http://localhost:8084/storage/get/avt-feed?ownerId=" + userId)
                .retrieve()
                .bodyToMono(String.class);

        return Mono.zip(
                sourceInfo,
                targetInfo,
                sourceAvatarUrl
        ).flatMap(tuples -> {
                    UserInfo sInfo = tuples.getT1();
                    List<UserInfo> tInfo = tuples.getT2();
                    String sAvtUrl = tuples.getT3();

                    var command = NotificationPostCommand.builder()
                            .actorInfo(sInfo)
                            .targetInfo(tInfo)
                            .actorAvatarUrl(sAvtUrl)
                            .data(Map.of())
                            .postId(postId)
                            .build();

                    ProducerRecord<String, Object> producerRecord = new ProducerRecord<>("notification_post_command", command);
                    SenderRecord<String, Object, String> senderRecord = SenderRecord.create(producerRecord, "profile operator");

                    return kafkaSender.send(Mono.just(senderRecord))
                            .then(redisPostResponse.opsForValue().get("p_response:" + postId))
                            .flatMap(postResponse -> {
                                log.info("Post response: {}", postResponse);
                                postResponse.setUserAvatarUrl(sAvtUrl);

                                return redisPostResponse.opsForValue().set("p_response:" + postId, postResponse);
                            })
                            .then();
                }
        );
    }

    // Rollback
    public Mono<Void> rollbackStorageService(String postId) {
        ProducerRecord<String, Object> producerRecord = new ProducerRecord<>("rollback_media_post_command", postId);
        SenderRecord<String, Object, String> senderRecord = SenderRecord.create(producerRecord, "profile operator");

        return kafkaSender.send(Mono.just(senderRecord))
                .then();
    }

    public Mono<Void> rollBackPostService(String postId) {
        ProducerRecord<String, Object> producerRecord = new ProducerRecord<>("rollback_post_service_command", postId);
        SenderRecord<String, Object, String> senderRecord = SenderRecord.create(producerRecord, "profile operator");

        return kafkaSender.send(Mono.just(senderRecord))
                .then();
    }

    public Mono<Void> rollBackPostIndexService(String postId) {
        ProducerRecord<String, Object> producerRecord = new ProducerRecord<>("rollback_post_index_command", postId);
        SenderRecord<String, Object, String> senderRecord = SenderRecord.create(producerRecord, "profile operator");

        return kafkaSender.send(Mono.just(senderRecord))
                .then();
    }

    public Mono<Void> rollBackFeedService(String postId) {
        ProducerRecord<String, Object> producerRecord = new ProducerRecord<>("rollback_feed_command", postId);
        SenderRecord<String, Object, String> senderRecord = SenderRecord.create(producerRecord, "profile operator");

        return kafkaSender.send(Mono.just(senderRecord))
                .then();
    }

    @KafkaListeners({
            @KafkaListener(topics = "save_post_fail_event", groupId = "orchestrator"),
            @KafkaListener(topics = "save_post_index_fail_event", groupId = "orchestrator"),
            @KafkaListener(topics = "cache_feed_fail_event", groupId = "orchestrator"),
            @KafkaListener(topics = "notification_post_fail_event", groupId = "orchestrator")
    })
    public void handleFailEvent(ConsumerRecord<String, String> record) {
        rollback(record.topic(), record.value()).subscribe();
    }


    private Mono<Void> rollback(String event, String postId) {
        return switch (event) {
            case "save_post_fail_event" -> rollbackStorageService(postId);
            case "save_post_index_fail_event" ->
                    rollbackStorageService(postId)
                            .then(rollBackPostService(postId));
            case "cache_feed_fail_event" ->
                    rollbackStorageService(postId)
                            .then(rollBackPostService(postId))
                            .then(rollBackPostIndexService(postId));
            case "notification_post_fail_event" ->
                    rollbackStorageService(postId)
                            .then(rollBackPostService(postId))
                            .then(rollBackPostIndexService(postId))
                            .then(rollBackFeedService(postId));
            default -> Mono.error(new RuntimeException("Save post error"));
        };
    }

}
