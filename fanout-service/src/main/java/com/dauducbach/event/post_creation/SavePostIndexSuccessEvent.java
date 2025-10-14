package com.dauducbach.event.post_creation;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)

public class SavePostIndexSuccessEvent {
    String postId;
    String userId;
    Long createAt;
}
