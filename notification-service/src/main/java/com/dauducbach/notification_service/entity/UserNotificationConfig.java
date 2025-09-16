package com.dauducbach.notification_service.entity;

import com.dauducbach.notification_service.constant.NotificationChanel;
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

@Table("notification_config")
public class UserNotificationConfig {
    @Id
    String id;
    String userId;
    NotificationType notificationType;
    NotificationChanel notificationChanel;
    boolean enabled;
    Instant updateAt;
}
