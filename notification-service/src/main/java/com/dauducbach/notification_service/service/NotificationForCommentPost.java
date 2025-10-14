package com.dauducbach.notification_service.service;

import com.dauducbach.event.comment_post.CommentRollback;
import com.dauducbach.event.comment_post.NotificationCommentPostCommand;
import com.dauducbach.event.comment_post.NotificationCommentPostSuccessEvent;
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

public class NotificationForCommentPost {
    KafkaSender<String, Object> kafkaSender;
    NotificationService notificationService;

    @KafkaListener(topics = "notification_comment_post_command")
    public void sendNotification(@Payload NotificationCommentPostCommand command) {
        log.info("1. Process command: {}", command);
        var pushRequestForPostOwner = PushRequest.builder()
                .actorInfo(command.actorInfo())
                .recipientInfo(List.of(command.postOwnerInfo()))
                .title("Binh luan moi")
                .imageUrl(command.actorAvatarUrl())
                .event(NotificationEvent.POST_COMMENTED)
                .entityType(EntityType.POST)
                .data(command.data())
                .build();


        var pushRequestForCommentedUser = PushRequest.builder()
                .actorInfo(command.actorInfo())
                .recipientInfo(List.of(command.postOwnerInfo()))
                .title("Binh luan moi")
                .body(command.actorInfo().getDisplayName()
                        + "da binh luan trong bai viet cua "
                        + command.postOwnerInfo().getDisplayName()
                        + ": "
                        + command.content())
                .imageUrl(command.actorAvatarUrl())
                .event(NotificationEvent.POST_COMMENTED)
                .entityType(EntityType.POST)
                .data(command.data())
                .build();

        if (command.targetInfo() != null) {
            var pushRequestForTargetUser = PushRequest.builder()
                    .actorInfo(command.actorInfo())
                    .targetInfo(command.targetInfo())
                    .recipientInfo(List.of(command.targetInfo()))
                    .title("Binh luan moi")
                    .body(command.actorInfo().getDisplayName() + "da phan hoi binh luan cua ban: " + command.content())
                    .imageUrl(command.actorAvatarUrl())
                    .event(NotificationEvent.POST_COMMENTED)
                    .entityType(EntityType.POST)
                    .data(command.data())
                    .build();
            log.info("4. Push request for target user: {}", pushRequestForTargetUser);

            pushRequestForPostOwner.setTargetInfo(command.targetInfo());
            pushRequestForPostOwner.setBody(command.actorInfo().getDisplayName()
                    + "da phan hoi binh luan cua "
                    + command.targetInfo().getDisplayName()
                    + "trong bai viet cua ban: "
                    + command.content());
            log.info("2. Push request for post owner user: {}", pushRequestForPostOwner);

            pushRequestForCommentedUser.setTargetInfo(command.targetInfo());
            log.info("3. Push request for commented user: {}", pushRequestForCommentedUser);

            Mono.zip(
                    notificationService.pushNotification(pushRequestForPostOwner),
                    notificationService.pushNotification(pushRequestForCommentedUser),
                    notificationService.pushNotification(pushRequestForTargetUser)
                    )
                    .then(Mono.defer(() -> {
                        var event = new NotificationCommentPostSuccessEvent(
                                command.commentId()
                        );
                        ProducerRecord<String, Object> producerRecord = new ProducerRecord<>("notification_comment_post_success_event", event);
                        SenderRecord<String, Object, String> senderRecord = SenderRecord.create(producerRecord, "comment post");
                        return kafkaSender.send(Mono.just(senderRecord))
                                .then();
                    }))
                    .onErrorResume(err -> {
                        log.info("Error: {}", err.getMessage());
                        var rollback = new CommentRollback(
                                command.commentId()
                        );
                        ProducerRecord<String, Object> producerRecord = new ProducerRecord<>("notification_comment_post_fail_event", rollback);
                        SenderRecord<String, Object, String> senderRecord = SenderRecord.create(producerRecord, "comment post");
                        return kafkaSender.send(Mono.just(senderRecord))
                                .then();
                    })
                    .subscribe();
        } else {
            pushRequestForPostOwner.setTargetInfo(null);
            pushRequestForPostOwner.setBody(command.actorInfo().getDisplayName()
                    + "da binh luan trong bai viet cua ban: "
                    + command.content());
            log.info("5. Push request for post owner user: {}", pushRequestForPostOwner);

            pushRequestForCommentedUser.setTargetInfo(null);
            log.info("6. Push request for commented user: {}", pushRequestForCommentedUser);

            Mono.zip(
                            notificationService.pushNotification(pushRequestForPostOwner),
                            notificationService.pushNotification(pushRequestForCommentedUser)
                    )
                    .then(Mono.defer(() -> {
                        var event = new NotificationCommentPostSuccessEvent(
                                command.commentId()
                        );
                        ProducerRecord<String, Object> producerRecord = new ProducerRecord<>("notification_comment_post_success_event", event);
                        SenderRecord<String, Object, String> senderRecord = SenderRecord.create(producerRecord, "comment post");
                        return kafkaSender.send(Mono.just(senderRecord))
                                .then();
                    }))
                    .onErrorResume(err -> {
                        log.info("Error: {}", err.getMessage());
                        var rollback = new CommentRollback(
                                command.commentId()
                        );
                        ProducerRecord<String, Object> producerRecord = new ProducerRecord<>("notification_comment_post_fail_event", rollback);
                        SenderRecord<String, Object, String> senderRecord = SenderRecord.create(producerRecord, "comment post");
                        return kafkaSender.send(Mono.just(senderRecord))
                                .then();
                    })
                    .subscribe();
        }
    }
}
