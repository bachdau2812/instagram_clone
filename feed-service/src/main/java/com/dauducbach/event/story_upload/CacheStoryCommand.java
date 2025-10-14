package com.dauducbach.event.story_upload;

import java.time.Instant;

public record CacheStoryCommand(
        String itemId,
        String userId,
        String mediaUrl,
        String audioUrl,
        Long createAt
) {
}
