package com.dauducbach.event.comment_post;

public record CommentBroadcastCommand(
        String commentId,
        String actorId,
        String postId,
        String postOf,
        String parentId,
        String parentIdOf,  // Comment parentId của parentIdOf
        String content
) {
}
