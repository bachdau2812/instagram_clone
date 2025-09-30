package com.dauducbach.post_service.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder

@Table("comments")
public class Comment {
    @Id
    String id;
    String postId;
    String userId;
    String content;
    String parentId;
    int likeCount;

    @JsonFormat(shape = JsonFormat.Shape.NUMBER)
    Instant createAt;

    @JsonFormat(shape = JsonFormat.Shape.NUMBER)
    Instant updateAt;
}
