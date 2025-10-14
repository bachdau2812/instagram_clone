package com.dauducbach.event.comment_post;

public record CommentPostIndexSuccessEvent(
        String commentId,
        String postId,
        String actorId
) {
}
