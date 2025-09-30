package com.dauducbach.post_service.entity;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder

public class PostAggregate {
    String postId;
    String userId;
    String content;
    String postAnalysis;
    List<String> tags;
    List<String> userLiked;
    List<String> userCommented;
}
