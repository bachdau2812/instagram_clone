package com.dauducbach.event.like_post;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder

public class NotificationLikeSuccessEvent {
    String actorId;
    String targetId;
    String postId;
}
