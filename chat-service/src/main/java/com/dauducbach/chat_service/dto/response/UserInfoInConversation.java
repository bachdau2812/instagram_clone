package com.dauducbach.chat_service.dto.response;

import com.dauducbach.chat_service.constant.GroupRole;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder

public class UserInfoInConversation {
    String userId;
    String userName;
    String avtUrl;
    GroupRole role;
    String lastReadMessageId;
    int unreadCount;
}
