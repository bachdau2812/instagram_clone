package com.dauducbach.event.comment_post;

public record CommentPostIndexCommand(
        String commentId,
        String postId,
        String actorId
) {
}
