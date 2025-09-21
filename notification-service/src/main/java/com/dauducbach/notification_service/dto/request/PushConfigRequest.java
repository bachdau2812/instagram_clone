package com.dauducbach.notification_service.dto.request;

import com.dauducbach.notification_service.constant.NotificationEvent;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder

public class PushConfigRequest {
    String userId;
    NotificationEvent notificationEvent;
    boolean isEnable;
}
