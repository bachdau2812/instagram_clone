package com.dauducbach.post_service.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

@Data
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder

@Table("posts")
public class Post {
    @Id
    String id;
    String userId;
    String content;

    @JsonFormat(shape = JsonFormat.Shape.NUMBER)
    Instant createAt;

    @JsonFormat(shape = JsonFormat.Shape.NUMBER)
    Instant updateAt;

}
