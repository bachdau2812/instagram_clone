package com.dauducbach.notification_service.service;

import com.dauducbach.notification_service.constant.NotificationChanel;
import com.dauducbach.notification_service.constant.NotificationEvent;
import com.dauducbach.notification_service.dto.request.AddUserPushTokenRequest;
import com.dauducbach.notification_service.dto.request.EmailRequest;
import com.dauducbach.notification_service.dto.request.PushRequest;
import com.dauducbach.notification_service.dto.request.TemplateRequest;
import com.dauducbach.notification_service.entity.NotificationDB;
import com.dauducbach.notification_service.entity.NotificationTemplate;
import com.dauducbach.notification_service.entity.UserPushToken;
import com.dauducbach.notification_service.repository.NotificationRepository;
import com.dauducbach.notification_service.repository.NotificationTemplateRepository;
import com.dauducbach.notification_service.repository.UserPushTokenRepository;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j

public class NotificationService {
    R2dbcEntityTemplate r2dbcEntityTemplate;
    JavaMailSender javaMailSender;
    NotificationRepository notificationRepository;
    UserPushTokenRepository userPushTokenRepository;

    public Mono<String> sendEmail(EmailRequest request) {
        return Flux.fromIterable(request.getAttachments())
                .collectList()
                .publishOn(Schedulers.boundedElastic())
                .flatMap(files -> {
                    try {
                        MimeMessage mimeMessage = javaMailSender.createMimeMessage();
                        MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

                        helper.setTo(request.getRecipient());
                        helper.setSubject(request.getSubject());
                        helper.setText(request.getHtmlContent(), true);

                        if (!files.isEmpty()) {
                            for (FilePart filePart : files) {
                                // Tạo file tạm với prefix/suffix hợp lệ
                                String originalFilename = filePart.filename();
                                String prefix = "upload-";
                                String suffix = originalFilename.contains(".")
                                        ? originalFilename.substring(originalFilename.lastIndexOf("."))
                                        : ".tmp";

                                File tempFile = File.createTempFile(prefix, suffix);

                                // Ghi dữ liệu file vào temp file (blocking)
                                filePart.transferTo(tempFile).block();

                                // Attach file
                                helper.addAttachment(originalFilename, new FileSystemResource(tempFile));
                            }
                        }

                        List<NotificationDB> notifications = Arrays.stream(request.getRecipient())
                                .map(email -> NotificationDB.builder()
                                        .id(UUID.randomUUID().toString())
                                        .userId(email)
                                        .actorId("SYSTEM")
                                        .notificationEvent(NotificationEvent.SYSTEM_ALERT)
                                        .notificationChanel(NotificationChanel.EMAIL)
                                        .message(request.getHtmlContent())
                                        .isRead(false)
                                        .createAt(Instant.now())
                                        .imageUrl("")
                                        .build()
                                )
                                .toList();

                        javaMailSender.send(mimeMessage);

                        return Flux.fromIterable(notifications)
                                .flatMap(notification -> r2dbcEntityTemplate.insert(NotificationDB.class).using(notification)
                                        .doOnError(throwable -> log.info("Error save notification: {}", throwable.getMessage())))
                                .then(Mono.just("Send complete"));
                    } catch (MessagingException | IOException e) {
                        return Mono.error(new RuntimeException("Failed to send email", e));
                    }
                })
                .onErrorReturn("Send Fail");
    }

    public Mono<String> pushNotification(PushRequest request) {
        return Flux.fromIterable(request.getRecipientInfo())
                .flatMap(userInfo ->
                        userPushTokenRepository.findByUserId(userInfo.getUserId())
                                .map(UserPushToken::getDeviceToken)
                        .flatMap(token -> {
                            log.info("{}", userInfo);
                            log.info("Device Token: {}", token);
                            Notification notification = Notification.builder()
                                    .setTitle(request.getTitle())
                                    .setBody(request.getBody())
                                    .build();

                            Message message = Message.builder()
                                    .setToken(token)
                                    .setNotification(notification)
                                    .putAllData(request.getData())
                                    .build();

                            var notificationDB = NotificationDB.builder()
                                    .id(UUID.randomUUID().toString())
                                    .actorId(request.getActorInfo().getUserId())
                                    .userId(userInfo.getUserId())
                                    .notificationChanel(NotificationChanel.PUSH)
                                    .notificationEvent(request.getEvent())
                                    .entityType(request.getEntityType())
                                    .entityId(request.getData().get("entityId"))
                                    .message(request.getBody())
                                    .isRead(false)
                                    .imageUrl(request.getImageUrl())
                                    .build();

                            return Mono.fromCallable(() -> FirebaseMessaging.getInstance().send(message))
                                    .then(r2dbcEntityTemplate.insert(NotificationDB.class).using(notificationDB));
                        })
                )
                .doOnError(err -> log.info("Error send push notification: {}", err.getMessage()))
                .then(Mono.just("Send Complete"));
    }

    public Mono<Void> addTokenDevice(AddUserPushTokenRequest request) {
        var pushToken = UserPushToken.builder()
                .id(UUID.randomUUID().toString())
                .deviceToken(request.getDeviceToken())
                .deviceId(request.getDeviceId())
                .userId(request.getUserId())
                .build();

        return r2dbcEntityTemplate.insert(UserPushToken.class).using(pushToken)
                .then();
    }

    public Mono<Void> createTemplate(TemplateRequest request) {
        var template = NotificationTemplate.builder()
                .event(request.getEvent())
                .content(request.getContent())
                .build();
        return r2dbcEntityTemplate.insert(NotificationTemplate.class).using(template)
                .then();
    }

    public Flux<NotificationDB> getNotificationOfUser(String userId) {
        return notificationRepository.findAllByUserId(userId);
    }
}
