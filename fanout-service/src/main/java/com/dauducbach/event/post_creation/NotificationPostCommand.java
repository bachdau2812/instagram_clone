package com.dauducbach.event.post_creation;

import com.dauducbach.fanout_service.constant.ProfileOperationType;
import com.dauducbach.fanout_service.dto.response.UserInfo;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder

public class NotificationPostCommand {
    UserInfo actorInfo;
    List<UserInfo> targetInfo;
    String actorAvatarUrl;
    Map<String, String> data;
    String postId;
}
