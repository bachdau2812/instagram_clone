package com.dauducbach.event.post_creation;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)

public class CacheFeedCommand {
    String postId;
    String userId;
    List<String> fanoutTo;
    Long score;
}
