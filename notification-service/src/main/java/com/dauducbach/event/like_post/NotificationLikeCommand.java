package com.dauducbach.event.like_post;

import com.dauducbach.notification_service.dto.request.UserInfo;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder
public class NotificationLikeCommand {
    UserInfo actorInfo;
    UserInfo targetInfo;
    String actorAvatarUrl;
    Map<String, String> data;
    String postId;
}
