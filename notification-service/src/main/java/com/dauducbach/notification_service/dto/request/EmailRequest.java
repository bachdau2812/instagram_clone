package com.dauducbach.notification_service.dto.request;

import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.http.codec.multipart.FilePart;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder

public class EmailRequest {
    String[] recipient;
    String subject;
    String htmlContent;
    List<FilePart> attachments;
}
