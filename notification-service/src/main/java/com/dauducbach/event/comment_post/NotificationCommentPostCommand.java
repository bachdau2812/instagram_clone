package com.dauducbach.event.comment_post;


import com.dauducbach.notification_service.dto.request.UserInfo;

import java.util.List;
import java.util.Map;

public record NotificationCommentPostCommand(
        String commentId,
        UserInfo actorInfo,
        UserInfo targetInfo,
        UserInfo postOwnerInfo,
        List<UserInfo> recipientInfo,
        String actorAvatarUrl,
        Map<String, String> data,
        String content
) {
}
