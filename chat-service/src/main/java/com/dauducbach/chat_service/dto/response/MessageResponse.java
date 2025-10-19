package com.dauducbach.chat_service.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder

public class MessageResponse {
    String messageId;
    String content;
    String senderId;
    String senderDisplayName;
    String senderAvtUrl;
    Instant createAt;
}
