package com.dauducbach.chat_service.entity;

import com.dauducbach.chat_service.constant.GroupRole;
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

@Table("user_inbox")
public class UserInbox {
    @Id
    String id;

    String userId;
    String inboxId;
    String lastReadMessageId;

    @Builder.Default
    int unreadCount = 0;

    @Builder.Default
    boolean isMuted = false;

    @Builder.Default
    boolean isPinned = false;

    GroupRole role;
    Instant joinedAt;
}
