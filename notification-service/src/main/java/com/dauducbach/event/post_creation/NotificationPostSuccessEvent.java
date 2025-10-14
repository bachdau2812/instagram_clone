package com.dauducbach.event.post_creation;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder

public class NotificationPostSuccessEvent {
    String postId;
}
