package com.dauducbach.event.post_creation;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)

public class SavePostSuccessEvent {
    String postId;
    String userId;
    String content;
    List<String> tags;
    Instant createAt;
    Instant updateAt;
}
