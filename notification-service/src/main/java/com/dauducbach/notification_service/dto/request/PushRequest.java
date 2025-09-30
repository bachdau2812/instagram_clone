package com.dauducbach.notification_service.dto.request;

import com.dauducbach.notification_service.constant.EntityType;
import com.dauducbach.notification_service.constant.NotificationEvent;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder

public class PushRequest {
    UserInfo actorInfo;
    UserInfo targetInfo;
    List<UserInfo> recipientInfo;     // userId, displayName
    String title;
    String body;
    String imageUrl;
    NotificationEvent event;
    EntityType entityType;
    Map<String, String> data;    // thong tin ve entity nhu: entityId, entityName, entityType, actorId, action, extra, url ...
}
