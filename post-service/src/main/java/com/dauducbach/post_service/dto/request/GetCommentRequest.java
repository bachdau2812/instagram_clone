package com.dauducbach.post_service.dto.request;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder

public class GetCommentRequest {
    String getChildOf;  // trường này dành cho thao tác bấm xem thêm ở các comment cha ở bài viết
    String postId;
    String postOf;

    @Builder.Default
    int currentIndex= 0;

    @Builder.Default
    int limit = 15;
}
