package com.dauducbach.event.user_service;

import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.http.codec.multipart.FilePart;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder

public class NotificationEvent {
    String[] recipient;
    String subject;
    String htmlContent;
    List<FilePart> attachments;
}
