package com.dauducbach.chat_service.dto.request;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder
@EqualsAndHashCode(callSuper = true)

public class SeenEvent extends WebSocketEvent{
    String viewerId;
    String senderId;
    String messageId;
    String inboxId;
}
