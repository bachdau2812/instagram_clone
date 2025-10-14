package com.dauducbach.event.story_upload;

import java.time.Instant;

public record SaveStorySuccessEvent(
        String itemId,
        Instant createAt
) {
}
