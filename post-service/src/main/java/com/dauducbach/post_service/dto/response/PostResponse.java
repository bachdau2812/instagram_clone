package com.dauducbach.post_service.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder

public class PostResponse {
    String postId;
    String userId;
    String userAvatarUrl;
    String content;
    List<String> tags;
    List<String> mediaUrl;

    @Builder.Default
    int likeCount = 0;

    @Builder.Default
    int commentCount = 0;
}
