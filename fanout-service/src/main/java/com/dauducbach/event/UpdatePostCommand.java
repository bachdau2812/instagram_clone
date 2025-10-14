package com.dauducbach.event;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder

public class UpdatePostCommand {
    String postId;
    String content;
    List<String> tags;
}
