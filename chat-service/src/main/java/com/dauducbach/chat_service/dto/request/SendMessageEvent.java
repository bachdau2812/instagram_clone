package com.dauducbach.chat_service.dto.request;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder
@EqualsAndHashCode(callSuper = true)
public class SendMessageEvent extends WebSocketEvent{
    String messageId;
    String recipientId;     // Danh cho lan dau tien nhan tin neu ma inboxId chua ton tai
    String content;
    String senderId;
    String inboxId;
}
