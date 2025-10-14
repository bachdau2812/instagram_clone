package com.dauducbach.event.post_creation;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder

public class CacheFeedSuccessEvent {
    String postId;
    List<String> followerList;
    String userId;
}
