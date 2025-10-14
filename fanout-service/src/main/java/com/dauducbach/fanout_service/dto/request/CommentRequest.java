package com.dauducbach.fanout_service.dto.request;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder

public class CommentRequest {
    String actorId;
    String postId;
    String postOf;
    String parentId;
    String parentIdOf;  // Phản hồi comment của user parentIdOf
    String content;
}
