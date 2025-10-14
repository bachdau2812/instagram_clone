package com.dauducbach.post_service.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder

public class UpdatePostRequest {
    String postId;
    String content;
    List<String> tags;
}
