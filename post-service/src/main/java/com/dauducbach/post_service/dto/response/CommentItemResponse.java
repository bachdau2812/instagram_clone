package com.dauducbach.post_service.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder

public class CommentItemResponse {
    String commentId;
    String postId;
    String postOfUserId;
    String postOfUsername;
    String content;
    String firstImg;
}
