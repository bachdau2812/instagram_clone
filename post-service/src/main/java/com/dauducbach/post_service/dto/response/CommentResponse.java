package com.dauducbach.post_service.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder

public class CommentResponse {
    String commentId;
    String actorId;
    String actorAvatar;
    String postId;
    String postOf;
    String parentId;
    String parentIdOf;
    String content;
}
