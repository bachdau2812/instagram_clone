package com.dauducbach.notification_service.entity;

import com.dauducbach.notification_service.constant.NotificationEvent;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Data
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder

@Table("push_config")
public class PushConfig {
    @Id
    String id;
    String userId;
    NotificationEvent notificationEvent;
    boolean isEnable;
}
