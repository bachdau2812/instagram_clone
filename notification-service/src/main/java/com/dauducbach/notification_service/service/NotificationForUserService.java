package com.dauducbach.notification_service.service;

import com.dauducbach.event.user_service.EmailVerifyEvent;
import com.dauducbach.event.user_service.ForgetPasswordEvent;
import com.dauducbach.event.user_service.NewPasswordEvent;
import com.dauducbach.event.user_service.NotificationEvent;
import com.dauducbach.notification_service.dto.request.EmailRequest;
import com.dauducbach.notification_service.repository.NotificationTemplateRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.util.ArrayList;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j

public class NotificationForUserService {
    NotificationService notificationService;
    NotificationTemplateRepository notificationTemplateRepository;

    @KafkaListener(topics = "user_creation_event")
    public void createUser(@Payload NotificationEvent event) {
        log.info("Event: {}" ,event);
        notificationTemplateRepository.findById(event.getHtmlContent())
                .flatMap(notificationTemplate -> {
                    var emailRequest = EmailRequest.builder()
                            .subject(event.getSubject())
                            .htmlContent(notificationTemplate.getContent())
                            .recipient(event.getRecipient())
                            .attachments(event.getAttachments())
                            .build();

                    return notificationService.sendEmail(emailRequest);
                })
                .subscribe();
    }

    @KafkaListener(topics = "email_verify_event")
    public void sendCode(@Payload EmailVerifyEvent event) {
        log.info("Event: {}", event);

        var emailRequest = EmailRequest.builder()
                .subject("Xac thuc email")
                .htmlContent("Ma xac thuc email cua nguoi dung " + event.getUsername() + " la: " + event.getCode())
                .recipient(new String[]{event.getEmail()})
                .attachments(new ArrayList<>())
                .build();

        notificationService.sendEmail(emailRequest).subscribe();
    }


    @KafkaListener(topics = "forget_password_event")
    public void sendCodeForForgetPassword(@Payload ForgetPasswordEvent event) {
        log.info("Event: {}", event);

        var emailRequest = EmailRequest.builder()
                .subject("Xac thuc email")
                .htmlContent("Ma xac thuc email cua email " + event.getEmail() + " la: " + event.getCode())
                .recipient(new String[]{event.getEmail()})
                .attachments(new ArrayList<>())
                .build();

        notificationService.sendEmail(emailRequest).subscribe();
    }

    @KafkaListener(topics = "new_password_event")
    public void sendNewPasswordToUser(@Payload NewPasswordEvent event) {
        log.info("Event: {}", event);

        var emailRequest = EmailRequest.builder()
                .subject("Mat khau moi")
                .htmlContent(String.format("""
                        Mat khau moi de dang nhap vao tai khoan cua ban la: %s
                        (Neu ban chon phan quen ca username thi username se la email cua ban)
                        """, event.getNewPassword()))
                .recipient(new String[]{event.getEmail()})
                .attachments(new ArrayList<>())
                .build();

        notificationService.sendEmail(emailRequest).subscribe();
    }
}
