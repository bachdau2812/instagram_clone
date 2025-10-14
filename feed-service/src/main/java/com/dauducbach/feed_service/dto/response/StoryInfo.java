package com.dauducbach.feed_service.dto.response;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

@Data
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder
public class StoryInfo {
    String itemId;
    String userId;
    String mediaUrl;
    String audioUrl;
    Long createAt;
}
