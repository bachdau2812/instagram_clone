package com.dauducbach.event;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder

public class NotificationForSendMessage {
    String senderId;
    String recipientId;
    Instant timestamp;
    String content;
    String type;
}
