package com.dauducbach.event.comment_post;

public record CommentUpdateFeedCommand(
        String commentId,
        String actorId,
        String postId,
        String postOf,
        String parentIdOf
) {
}
