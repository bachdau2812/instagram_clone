package com.dauducbach.notification_service.controller;

import com.dauducbach.notification_service.dto.request.EmailRequest;
import com.dauducbach.notification_service.service.NotificationService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@RestController
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j

public class NotificationController {
    NotificationService notificationService;

    @PostMapping(
            value = "/send-email",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public Mono<String> sendEmail(
            @RequestPart("recipient") String recipient,
            @RequestPart("subject") String subject,
            @RequestPart("htmlContent") String htmlContent,
            @RequestPart(value = "attachments", required = false) List<FilePart> attachments
    ) {

        log.info("In controller");

        String[] emails = recipient.split(",");
        EmailRequest request = new EmailRequest();
        request.setRecipient(emails);
        request.setSubject(subject);
        request.setHtmlContent(htmlContent);

        request.setAttachments(Objects.requireNonNullElseGet(attachments, ArrayList::new));

        return notificationService.sendEmail(request);
    }
}
