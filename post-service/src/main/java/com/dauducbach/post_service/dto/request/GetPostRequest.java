package com.dauducbach.post_service.dto.request;

import com.dauducbach.post_service.constant.MediaType;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder

public class GetPostRequest {
    String postId;
    MediaType mediaType;
}
