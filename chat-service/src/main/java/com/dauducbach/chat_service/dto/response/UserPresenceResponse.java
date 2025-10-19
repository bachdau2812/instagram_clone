package com.dauducbach.chat_service.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder

public class UserPresenceResponse {
    String userId;
    boolean isOnline;
    Long lastSeen;
}
