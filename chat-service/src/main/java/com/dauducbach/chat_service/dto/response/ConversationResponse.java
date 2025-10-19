package com.dauducbach.chat_service.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder

public class ConversationResponse {
    String inboxId;
    String inboxName;
    Instant createAt;
    List<UserInfoInConversation> userInfoInConversations;
    List<MessageResponse> messageResponses;
}
