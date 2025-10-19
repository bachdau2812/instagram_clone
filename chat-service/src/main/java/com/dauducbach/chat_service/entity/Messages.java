package com.dauducbach.chat_service.entity;

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

@Table("messages")
public class Messages {
    @Id
    String id;
    String inboxUid;
    String senderId;
    String content;
    Instant createdAt;
    Instant updateAt;
}
