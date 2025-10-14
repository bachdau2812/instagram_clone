package com.dauducbach.event.comment_post;

public record CommentPostCommand(
        String commentId,
        String actorId,
        String postId,
        String postOf,      // Bình luận nằm trong bài viết của postOf
        String parentId,
        String parentIdOf,  // Comment parentId của parentIdOf
        String content
) {
}
