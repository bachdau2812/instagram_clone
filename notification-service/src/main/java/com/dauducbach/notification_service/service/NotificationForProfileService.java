package com.dauducbach.notification_service.service;

import com.dauducbach.event.profile_operation.NotificationUserConnectCommand;
import com.dauducbach.event.profile_operation.NotificationUserConnectFailEvent;
import com.dauducbach.event.profile_operation.NotificationUserConnectSuccessEvent;
import com.dauducbach.notification_service.constant.NotificationEvent;
import com.dauducbach.notification_service.dto.request.PushRequest;
import com.dauducbach.notification_service.repository.PushConfigRepository;
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

public class NotificationForProfileService {
    NotificationService notificationService;
    PushConfigRepository pushConfigRepository;
    KafkaSender<String, Object> kafkaSender;

    @KafkaListener(topics = "notification_user_connect")
    public void profileOperatorHandle(@Payload NotificationUserConnectCommand command) {
        log.info("Send Notification for Follow: {}", command);

        pushConfigRepository.findByUserIdAndNotificationEvent(
                command.targetInfo().getUserId(),
                NotificationEvent.USER_FOLLOWED
        ).flatMap(pushConfig -> {
            log.info("{}", pushConfig);
            if (pushConfig.isEnable()) {
                var pushRequest = PushRequest.builder()
                        .actorInfo(command.actorInfo())
                        .targetInfo(command.targetInfo())
                        .recipientInfo(List.of(command.targetInfo()))
                        .title("Người theo dõi mới")
                        .body(command.actorInfo().getDisplayName() + " đã gửi yêu cầu follow")
                        .data(command.data())
                        .imageUrl(command.actorAvatarUrl())
                        .build();

                return notificationService.pushNotification(pushRequest)
                        // Xử lý lỗi gửi push
                        .onErrorResume(err -> {
                            log.info("Send fail: {}", err.getMessage());

                            var failEvent = new NotificationUserConnectFailEvent(
                                    command.type(),
                                    command.actorInfo().getUserId(),
                                    command.targetInfo().getUserId()
                            );

                            ProducerRecord<String, Object> record =
                                    new ProducerRecord<>("notification_user_connect_fail", failEvent);
                            SenderRecord<String, Object, String> senderRecord =
                                    SenderRecord.create(record, "profile operator");

                            return kafkaSender.send(Mono.just(senderRecord)).then(Mono.just("Complete"));
                        })
                        // Nếu gửi push thành công thì phát sự kiện success
                        .then(Mono.defer(() -> {
                            log.info("Send complete");

                            var successEvent = new NotificationUserConnectSuccessEvent(
                                    command.type(),
                                    command.actorInfo().getUserId(),
                                    command.targetInfo().getUserId()
                            );

                            ProducerRecord<String, Object> record =
                                    new ProducerRecord<>("notification_user_connect_success", successEvent);
                            SenderRecord<String, Object, String> senderRecord =
                                    SenderRecord.create(record, "profile operator");

                            return kafkaSender.send(Mono.just(senderRecord)).then();
                        }));
            }
            return Mono.empty();
        }).subscribe();
    }
}
