package com.dauducbach.fanout_service.service;

import com.dauducbach.event.profile_operation.*;
import com.dauducbach.fanout_service.dto.request.ProfileOperationRequest;
import com.dauducbach.fanout_service.dto.request.UserBasicInfoRequest;
import com.dauducbach.fanout_service.dto.response.UserInfo;
import com.dauducbach.fanout_service.repository.ProfileIndexRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderRecord;

import java.util.HashMap;
import java.util.List;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j

public class FanoutForProfileService {
    ProfileIndexRepository profileIndexRepository;
    KafkaSender<String, Object> kafkaSender;
    private final WebClient webClient;

    // Start Saga
    public Mono<Void> profileOperationHandle(ProfileOperationRequest request) {
        log.info("Start Follow Saga");
        var profileCommand = new ProfileOperationCommand(request.getType(), request.getSourceId(), request.getTargetId());

        ProducerRecord<String, Object> producerRecord = new ProducerRecord<>("profile_user_connect", profileCommand);
        SenderRecord<String, Object, String> senderRecord = SenderRecord.create(producerRecord, "profile operator");

        return kafkaSender.send(Mono.just(senderRecord))
                .then();
    }

    @KafkaListener(topics = "profile_user_connect_success", groupId = "orchestrator")
    public void saveToProfileServiceComplete(@Payload ProfileOperationSuccessEvent event) {
        log.info("Save to Profile Service complete");
        var profileIndexUserConnectCommand = new ProfileIndexUserConnectCommand(
                event.type(),
                event.sourceId(),
                event.targetId()
        );

        ProducerRecord<String, Object> producerRecord = new ProducerRecord<>("profile_index_user_connect", profileIndexUserConnectCommand);
        SenderRecord<String, Object, String> senderRecord = SenderRecord.create(producerRecord, "profile operator");

        kafkaSender.send(Mono.just(senderRecord)).subscribe();
    }

    @KafkaListener(topics = "profile_index_user_connect_success", groupId = "orchestrator")
    public void sendNotification(@Payload ProfileIndexUserConnectSuccessEvent event) {
        log.info("Save to Profile Index complete");
        Mono<UserInfo> sourceInfo = webClient.post()
                .uri("http://localhost:8081/profile/get-basic-info")
                .bodyValue(UserBasicInfoRequest.builder()
                        .userId(List.of(event.sourceId()))
                        .build()
                )
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<UserInfo>>() {})
                .map(List::getFirst);

        Mono<UserInfo> targetInfo = webClient.post()
                .uri("http://localhost:8081/profile/get-basic-info")
                .bodyValue(UserBasicInfoRequest.builder()
                        .userId(List.of(event.targetId()))
                        .build()
                )
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<UserInfo>>() {})
                .map(List::getFirst);

        Mono<String> sourceAvatarUrl = webClient.get()
                .uri("http://localhost:8084/storage/get/avt-feed?ownerId=" + event.sourceId())
                .retrieve()
                .bodyToMono(String.class);

        Mono.zip(
                sourceInfo,
                targetInfo,
                sourceAvatarUrl
        ).flatMap(tuples -> {
            UserInfo sInfo = tuples.getT1();
            UserInfo tInfo = tuples.getT2();
            String sAvtUrl = tuples.getT3();

            var command = new NotificationUserConnectCommand(
                    event.type(),
                    sInfo,
                    tInfo,
                    sAvtUrl,
                    new HashMap<>()
            );

            ProducerRecord<String, Object> producerRecord = new ProducerRecord<>("notification_user_connect", command);
            SenderRecord<String, Object, String> senderRecord = SenderRecord.create(producerRecord, "profile operator");

            return kafkaSender.send(Mono.just(senderRecord))
                    .then(Mono.fromRunnable(() ->
                            log.info("Sent notification_user_connect event: {}", command)
                    ));
        }).subscribe();
    }

    // ============ COMPENSATION ============

    @KafkaListener(topics = "profile_user_connect_fail", groupId = "orchestrator")
    public void saveToProfileServiceFail(@Payload ProfileOperationFailEvent event) {
        Mono.defer(() -> Mono.error(new RuntimeException("Follow fail"))).subscribe();
    }

    @KafkaListener(topics = "profile_index_user_connect_fail", groupId = "orchestrator")
    public void rollBackSaveToProfileService(@Payload ProfileIndexUserConnectFailEvent event) {
        var profileUserConnectCommand = new ProfileOperationCommand(
                event.type(),
                event.sourceId(),
                event.targetId()
        );

        ProducerRecord<String, Object> producerRecord = new ProducerRecord<>("rollback_profile_user_connect", profileUserConnectCommand);
        SenderRecord<String, Object, String> senderRecord = SenderRecord.create(producerRecord, "profile operator");

        kafkaSender.send(Mono.just(senderRecord))
                .then(Mono.defer(() -> Mono.error(new RuntimeException("Follow fail"))))
                .subscribe();
    }

    @KafkaListener(topics = "notification_user_connect_fail")
    public void rollbackSaveProfileIndexAndProfileService(@Payload NotificationUserConnectFailEvent event) {
        var profileUserConnectCommand = new ProfileOperationCommand(
                event.type(),
                event.sourceId(),
                event.targetId()
        );

        var profileIndexUserConnectCommand = new ProfileIndexUserConnectCommand(
                event.type(),
                event.sourceId(),
                event.targetId()
        );

        ProducerRecord<String, Object> producerRecord = new ProducerRecord<>("rollback_profile_user_connect", profileUserConnectCommand);
        SenderRecord<String, Object, String> senderRecord = SenderRecord.create(producerRecord, "profile operator");

        ProducerRecord<String, Object> producerRecord2 = new ProducerRecord<>("rollback_profile_index_user_connect", profileIndexUserConnectCommand);
        SenderRecord<String, Object, String> senderRecord2 = SenderRecord.create(producerRecord, "profile operator");

        Mono.when(
                kafkaSender.send(Mono.just(senderRecord2)), kafkaSender.send(Mono.just(senderRecord))
        ).subscribe();
    }
}
