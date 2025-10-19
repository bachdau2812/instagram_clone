package com.dauducbach.chat_service.entity;

import com.dauducbach.chat_service.constant.InboxType;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder

@Table("inbox")
public class Inbox {
    @Id
    String id;
    InboxType type;
    String name;
    String lastMessageId;
    String lastSentUserId;
    Instant createdAt;
}
