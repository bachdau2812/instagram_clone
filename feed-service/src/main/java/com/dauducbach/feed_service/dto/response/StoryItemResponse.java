package com.dauducbach.feed_service.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder

public class StoryItemResponse {
    String itemId;
    String mediaUrl;
    String audioUrl;
    Long createAt;
    boolean isExpired;
}
