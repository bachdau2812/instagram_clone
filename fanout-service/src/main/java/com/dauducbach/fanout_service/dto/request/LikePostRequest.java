package com.dauducbach.fanout_service.dto.request;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder

public class LikePostRequest {
    String actorId;
    String targetId;
    String postId;
}
