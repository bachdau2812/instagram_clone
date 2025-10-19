package com.dauducbach.notification_service.service;

import com.dauducbach.event.NotificationForSendMessage;
import com.dauducbach.notification_service.constant.EntityType;
import com.dauducbach.notification_service.constant.NotificationEvent;
import com.dauducbach.notification_service.dto.request.GetAvatarRequest;
import com.dauducbach.notification_service.dto.request.PushRequest;
import com.dauducbach.notification_service.dto.request.UserBasicInfoRequest;
import com.dauducbach.notification_service.dto.request.UserInfo;
import com.dauducbach.notification_service.dto.response.GetAvatarResponse;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@Service
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RequiredArgsConstructor
@Slf4j

public class NotificationForChatService {
    NotificationService notificationService;
    GetUserInfo getUserInfo;

    @KafkaListener(topics = "send_notification_for_chat_event")
    public void handleNotificationChat(@Payload NotificationForSendMessage event) {
        log.info("1. Send notification for chat: {}", event);
        Mono<List<UserInfo>> userInfo = getUserInfo.getUserInfo(List.of(event.getRecipientId(), event.getSenderId()));

        Mono<Map<String, String>> userAvt = getUserInfo.getListAvtOfListUser(List.of(event.getRecipientId(), event.getSenderId()));

        Mono.zip(userInfo, userAvt)
                .flatMap(objects -> {
                    var actor = objects.getT1().stream().filter(userInfo1 -> userInfo1.getUserId().equals(event.getSenderId())).toList().get(0);
                    var target = objects.getT1().stream().filter(userInfo1 -> userInfo1.getUserId().equals(event.getRecipientId())).toList().get(0);
                    log.info("2. Get user info complete: {}", event);

                    StringBuilder body = new StringBuilder();
                    if (event.getType().equals("private")) {
                        body.append(" da gui cho ban mot tin nhan: ");
                    } else {
                        body.append(" da gui mot tin nhan trong nhom cua ban: ");
                    }

                    var pushRequest = PushRequest.builder()
                            .actorInfo(actor)
                            .targetInfo(target)
                            .recipientInfo(List.of(target))
                            .title("New Message")
                            .body(actor.getDisplayName() + body + event.getContent())
                            .data(Map.of())
                            .imageUrl(objects.getT2().get(event.getSenderId()))
                            .entityType(EntityType.MESSAGE)
                            .event(NotificationEvent.MESSAGE_SENT)
                            .build();

                    return notificationService.pushNotification(pushRequest)
                            .doOnSuccess(s -> log.info("3. Send notification complete: {}", s))
                            .doOnError(throwable -> log.info("3. Send notification fail: {}", throwable.getMessage()));
                })
                .subscribe();
    }
}
