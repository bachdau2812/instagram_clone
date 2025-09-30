package com.dauducbach.event.profile_operation;

import com.dauducbach.fanout_service.constant.ProfileOperationType;
import com.dauducbach.fanout_service.dto.response.UserInfo;

import java.util.Map;

public record NotificationUserConnectCommand(
        ProfileOperationType type,
        UserInfo actorInfo,
        UserInfo targetInfo,
        String actorAvatarUrl,
        Map<String, String> data
) {

}
