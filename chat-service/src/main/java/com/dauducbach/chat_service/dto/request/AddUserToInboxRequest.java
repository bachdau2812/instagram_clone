package com.dauducbach.chat_service.dto.request;

import com.dauducbach.chat_service.constant.GroupRole;
import com.dauducbach.chat_service.constant.InboxType;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder

public class AddUserToInboxRequest {
    String userId;
    String inboxId;
    InboxType inboxType;
    GroupRole role;
}
