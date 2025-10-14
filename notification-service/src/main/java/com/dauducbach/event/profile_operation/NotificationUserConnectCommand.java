package com.dauducbach.event.profile_operation;


import com.dauducbach.notification_service.constant.ProfileOperationType;
import com.dauducbach.notification_service.dto.request.UserInfo;

import java.util.Map;

public record NotificationUserConnectCommand(
        ProfileOperationType type,
        UserInfo actorInfo,
        UserInfo targetInfo,
        String actorAvatarUrl,
        Map<String, String> data
) {

}
