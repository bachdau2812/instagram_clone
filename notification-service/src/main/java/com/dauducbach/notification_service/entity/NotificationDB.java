package com.dauducbach.notification_service.entity;

import com.dauducbach.notification_service.constant.EntityType;
import com.dauducbach.notification_service.constant.NotificationChanel;
import com.dauducbach.notification_service.constant.NotificationEvent;
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
public class NotificationDB {
    @Id
    String id;
    String userId;  // email with email notification and userId with push notification
    String actorId; // nullable
    NotificationEvent notificationEvent;
    EntityType entityType;
    String entityId; // nullable
    String message;
    boolean isRead;
    Instant createAt;
    NotificationChanel notificationChanel;
    String imageUrl;
}
