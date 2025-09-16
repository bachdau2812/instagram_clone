package com.dauducbach.notification_service.controller;

import com.dauducbach.notification_service.service.NotificationService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)

public class NotificationController {
    NotificationService notificationService;

    @GetMapping("/send-email")
    Mono<Void> sendEmail(
            @RequestParam String to,
            @RequestParam String subject,
            @RequestParam String context) {
        return notificationService.sendEmail(to, subject, context);
    }
}
