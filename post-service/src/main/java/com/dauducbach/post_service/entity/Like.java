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

@Table("likes")
public class Like {
    @Id
    String id;
    String targetId;
    String userId;
    String type;    // comment, post

    @JsonFormat(shape = JsonFormat.Shape.NUMBER)
    Instant timestamp;
}
