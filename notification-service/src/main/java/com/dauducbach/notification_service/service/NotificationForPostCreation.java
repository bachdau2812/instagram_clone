package com.dauducbach.notification_service.service;

import com.dauducbach.event.post_creation.NotificationPostCommand;
import com.dauducbach.event.post_creation.NotificationPostSuccessEvent;
import com.dauducbach.notification_service.constant.EntityType;
import com.dauducbach.notification_service.constant.NotificationEvent;
import com.dauducbach.notification_service.dto.request.PushRequest;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderRecord;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j

public class NotificationForPostCreation {
    NotificationService notificationService;
    KafkaSender<String, Object> kafkaSender;

    @KafkaListener(topics = "notification_post_command")
    public void notification(@Payload NotificationPostCommand command) {
        log.info("In Notification post: {}", command);
        var pushRequest = PushRequest.builder()
                .actorInfo(command.getActorInfo())
                .recipientInfo(command.getTargetInfo())
                .title("New Post")
                .body(command.getActorInfo().getDisplayName() + "posted a new article")
                .event(NotificationEvent.POST_CREATED)
                .entityType(EntityType.POST)
                .data(command.getData())
                .build();

        notificationService.pushNotification(pushRequest)
                .flatMap(res -> {
                    var event = NotificationPostSuccessEvent.builder()
                            .postId(command.getPostId())
                            .build();

                    ProducerRecord<String, Object> producerRecord = new ProducerRecord<>("notification_post_success_event", event);
                    SenderRecord<String, Object, String> senderRecord = SenderRecord.create(producerRecord, "post creation");

                    return kafkaSender.send(Mono.just(senderRecord))
                            .doOnComplete(() -> log.info("Notification Complete"))
                            .then();
                })
                .onErrorResume(err -> {
                    log.info("Error: {}", err.getMessage());
                    ProducerRecord<String, Object> producerRecord = new ProducerRecord<>("notification_post_fail_event", command.getPostId());
                    SenderRecord<String, Object, String> senderRecord = SenderRecord.create(producerRecord, "post creation");

                    return kafkaSender.send(Mono.just(senderRecord))
                            .then();
                })
                .subscribe();

    }
}
