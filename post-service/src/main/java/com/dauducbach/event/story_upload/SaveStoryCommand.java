package com.dauducbach.event.story_upload;

public record SaveStoryCommand(
        String itemId,
        String userId,
        String mediaUrl,
        String audioUrl
) {
}
