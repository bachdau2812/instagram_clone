package com.dauducbach.event.story_upload;


import com.dauducbach.notification_service.dto.request.UserInfo;

import java.util.List;

public record NotificationStoryCommand(
        String itemId,
        UserInfo actorInfo,
        String actorAvt,
        List<UserInfo> recipientsInfo
) {
}
