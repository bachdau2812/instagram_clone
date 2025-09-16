package com.dauducbach.notification_service.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j

public class NotificationService {
    JavaMailSender javaMailSender;

    public Mono<Void> sendEmail(String to, String subject, String content) {
        return Mono.fromRunnable(() -> {
            try {
                MimeMessage message = javaMailSender.createMimeMessage();
                MimeMessageHelper messageHelper = new MimeMessageHelper(message, true, "UTF-8");

                messageHelper.setTo(to);
                messageHelper.setSubject(subject);
                messageHelper.setText(content, true);

                javaMailSender.send(message);
            } catch (MessagingException e) {
                throw new RuntimeException(e);
            }
        }).subscribeOn(Schedulers.boundedElastic())
                .then();
    }
}
