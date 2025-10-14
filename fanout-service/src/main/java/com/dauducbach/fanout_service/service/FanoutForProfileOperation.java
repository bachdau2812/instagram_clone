package com.dauducbach.fanout_service.service;

import com.dauducbach.event.post_creation.NotificationPostCommand;
import com.dauducbach.event.post_creation.NotificationPostSuccessEvent;
import com.dauducbach.event.profile_operation.*;
import com.dauducbach.fanout_service.constant.ProfileOperationType;
import com.dauducbach.fanout_service.dto.request.ProfileOperationRequest;
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

public class FanoutForProfileOperation {
    KafkaSender<String, Object> kafkaSender;
    WebClient webClient;

    // Start saga
    public Mono<Void> profileOperation(ProfileOperationRequest request) {
        log.info("1. Start saga");
        var command = new ProfileOperationCommand(
                request.getType(),
                request.getSourceId(),
                request.getTargetId()
        );

        ProducerRecord<String, Object> producerRecord = new ProducerRecord<>("profile_operation_command", command);
        SenderRecord<String, Object, String> senderRecord = SenderRecord.create(producerRecord, "profile operation");

        return kafkaSender.send(Mono.just(senderRecord))
                .then();
    }

    // update in profile index
    @KafkaListener(topics = "profile_operation_success_event")
    public void updateProfileIndex(@Payload ProfileOperationSuccessEvent event) {
        log.info("2. Process profile service complete");
        var command = new ProfileIndexUserConnectCommand(
                event.type(),
                event.sourceId(),
                event.targetId()
        );

        ProducerRecord<String, Object> producerRecord = new ProducerRecord<>("profile_index_operation_command", command);
        SenderRecord<String, Object, String> senderRecord = SenderRecord.create(producerRecord, "profile operation");

        kafkaSender.send(Mono.just(senderRecord))
                .subscribe();
    }

    // send notification

    @KafkaListener(topics = "profile_index_operation_success_event")
    public void sendNotification(@Payload ProfileIndexUserConnectSuccessEvent event) {
        log.info("3. Process profile index complete");
        sendNotification(event.sourceId(), event.targetId(), event.type())
                .subscribe();
    }

    private Mono<Void> sendNotification(String userId, String targetId, ProfileOperationType type) {
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
                        .userId(List.of(targetId))
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

                    var command = new NotificationUserConnectCommand(
                            type,
                            sInfo,
                            tInfo.get(0),
                            sAvtUrl,
                            Map.of()
                    );

                    ProducerRecord<String, Object> producerRecord = new ProducerRecord<>("notification_profile_operation_command", command);
                    SenderRecord<String, Object, String> senderRecord = SenderRecord.create(producerRecord, "profile operator");

                    return kafkaSender.send(Mono.just(senderRecord))
                            .then();
                }
        );
    }

    @KafkaListener(topics = "notification_profile_operation_success_event")
    public void profileOperationComplete(@Payload NotificationUserConnectSuccessEvent event) {
        log.info("4. Operation complete: {}", event);
    }

    // rollback

    public Mono<Void> rollbackProfileService(ProfileOperationRollback operationRollback) {
        ProducerRecord<String, Object> producerRecord = new ProducerRecord<>("rollback_profile_operation", operationRollback);
        SenderRecord<String, Object, String> senderRecord = SenderRecord.create(producerRecord, "profile operator");

        return kafkaSender.send(Mono.just(senderRecord))
                .then();
    }

    public Mono<Void> rollbackProfileIndex(ProfileOperationRollback operationRollback) {
        ProducerRecord<String, Object> producerRecord = new ProducerRecord<>("rollback_profile_index_operation", operationRollback);
        SenderRecord<String, Object, String> senderRecord = SenderRecord.create(producerRecord, "profile operator");

        return kafkaSender.send(Mono.just(senderRecord))
                .then();
    }

    @KafkaListeners({
            @KafkaListener(topics = "profile_operation_fail_event"),
            @KafkaListener(topics = "profile_index_operation_fail_event"),
            @KafkaListener(topics = "notification_profile_operation_fail_event")
    })
    public void handlerFailEvent(ConsumerRecord<String, ProfileOperationRollback> record) {
        rollback(record.topic(), record.value())
                .subscribe();
    }

    public Mono<Void> rollback(String event, ProfileOperationRollback operationRollback) {
        return switch (event){
            case "profile_operation_fail_event" -> Mono.error(new RuntimeException("Error operation"));
            case "profile_index_operation_fail_event" -> rollbackProfileIndex(operationRollback);
            case "notification_profile_operation_fail_event" ->
                    rollbackProfileIndex(operationRollback)
                            .then(rollbackProfileIndex(operationRollback));
            default -> Mono.empty();
        };
    }
}
