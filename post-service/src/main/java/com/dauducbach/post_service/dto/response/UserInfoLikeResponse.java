package com.dauducbach.post_service.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder

public class UserInfoLikeResponse {
    String userId;
    String username;
    String displayName;
}
