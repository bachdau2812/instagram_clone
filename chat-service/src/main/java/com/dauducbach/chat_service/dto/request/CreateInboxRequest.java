package com.dauducbach.chat_service.dto.request;

import com.dauducbach.chat_service.constant.InboxType;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder

public class CreateInboxRequest {
    InboxType type;
    String recipientId;     // Danh cho one-to-one message
    String name;
}
