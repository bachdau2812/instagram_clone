package com.dauducbach.notification_service.service;

import com.dauducbach.event.like_post.NotificationLikeSuccessEvent;
import com.dauducbach.event.post_creation.NotificationPostSuccessEvent;
import com.dauducbach.event.like_post.NotificationLikeCommand;
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

import java.util.List;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j

public class NotificationForLikePost {
    NotificationService notificationService;
    KafkaSender<String, Object> kafkaSender;

    @KafkaListener(topics = "notification_like_command")
    public void likePost(@Payload NotificationLikeCommand command) {
        var request = PushRequest.builder()
                .actorInfo(command.getActorInfo())
                .recipientInfo(List.of(command.getTargetInfo()))
                .data(command.getData())
                .title("LIKE POST")
                .body(command.getActorInfo().getDisplayName() + "just like your post")
                .event(NotificationEvent.LIKE_POST)
                .imageUrl(command.getActorAvatarUrl())
                .entityType(EntityType.POST)
                .build();

        notificationService.pushNotification(request)
                .flatMap(res -> {
                    var event = NotificationLikeSuccessEvent.builder()
                            .postId(command.getPostId())
                            .actorId(command.getActorInfo().getUserId())
                            .targetId(command.getTargetInfo().getUserId())
                            .build();

                    ProducerRecord<String, Object> producerRecord = new ProducerRecord<>("notification_like_success_event", event);
                    SenderRecord<String, Object, String> senderRecord = SenderRecord.create(producerRecord, "post creation");

                    return kafkaSender.send(Mono.just(senderRecord))
                            .doOnComplete(() -> log.info("Notification Complete"))
                            .then();
                })
                .onErrorResume(err -> {
                    log.info("Error: {}", err.getMessage());
                    ProducerRecord<String, Object> producerRecord = new ProducerRecord<>("notification_like_fail_event", command.getPostId());
                    SenderRecord<String, Object, String> senderRecord = SenderRecord.create(producerRecord, "post creation");

                    return kafkaSender.send(Mono.just(senderRecord))
                            .then();
                })
                .subscribe();
    }
}
