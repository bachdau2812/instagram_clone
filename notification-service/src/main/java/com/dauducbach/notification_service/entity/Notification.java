package com.dauducbach.notification_service.entity;

import com.dauducbach.notification_service.constant.EntityType;
import com.dauducbach.notification_service.constant.NotificationType;
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

@Table("notifications")
public class Notification {
    @Id
    String id;
    String userId;
    String actorId; // nullable
    NotificationType notificationType;
    EntityType entityType;
    String entityId; // nullable
    String message;
    boolean isRead;
    Instant createAt;
}
