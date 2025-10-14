package com.dauducbach.feed_service.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder

public class PostIndex {
    String id;
    String userId;
    String content;
    List<String> tags;
    List<String> likedId;
    List<String> commentedId;
    Long createAt;
    Long updateAt;
    List<Float> embedding;
}
