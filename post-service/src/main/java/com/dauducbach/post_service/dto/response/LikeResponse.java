package com.dauducbach.post_service.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder

public class LikeResponse {
    String userId;
    String userName;
    String displayName;
    String avatarUrl;
}
