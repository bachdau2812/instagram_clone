package com.dauducbach.notification_service.service;

import com.dauducbach.event.story_upload.NotificationStoryCommand;
import com.dauducbach.event.story_upload.NotificationStorySuccessEvent;
import com.dauducbach.event.story_upload.StoryRollback;
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

import java.util.Map;

@Service
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RequiredArgsConstructor
@Slf4j

public class NotificationForStoryUpload {
    NotificationService notificationService;
    KafkaSender<String, Object> kafkaSender;

    @KafkaListener(topics = "notification_story_command")
    public void sendNotification(@Payload NotificationStoryCommand command) {
        var request = PushRequest.builder()
                .actorInfo(command.actorInfo())
                .recipientInfo(command.recipientsInfo())
                .title("New Story")
                .body(command.actorInfo().getDisplayName() + "da dang 1 tin moi")
                .event(NotificationEvent.POST_COMMENTED)
                .entityType(EntityType.POST)
                .data(Map.of())
                .imageUrl(command.actorAvt())
                .build();

        notificationService.pushNotification(request)
                .then(Mono.defer(() -> {
                    var event = new NotificationStorySuccessEvent(
                            command.itemId()
                    );

                    ProducerRecord<String, Object> producerRecord = new ProducerRecord<>("notification_story_success_event", event);
                    SenderRecord<String, Object, String> senderRecord = SenderRecord.create(producerRecord, "story creation");

                    return kafkaSender.send(Mono.just(senderRecord))
                            .then();
                }))
                .onErrorResume(err -> {
                    var rollback = new StoryRollback(command.itemId(), command.actorInfo().getUserId());

                    ProducerRecord<String, Object> producerRecord = new ProducerRecord<>("notification_story_fail_event", rollback);
                    SenderRecord<String, Object, String> senderRecord = SenderRecord.create(producerRecord, "story creation");

                    return kafkaSender.send(Mono.just(senderRecord))
                            .then();
                })
                .subscribe();
    }
}
