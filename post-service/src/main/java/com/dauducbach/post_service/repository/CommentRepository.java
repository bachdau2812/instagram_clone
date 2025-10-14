package com.dauducbach.post_service.repository;

import com.dauducbach.post_service.dto.response.CommentHistoryResponse;
import com.dauducbach.post_service.entity.Comment;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface CommentRepository extends ReactiveCrudRepository<Comment, String> {
    Mono<Integer> countCommentByPostId(String postId);

    @Query(value = """
                SELECT *
                FROM comments
                WHERE post_id = :postId 
                  AND parent_id IS NULL
                ORDER BY created_at DESC
                LIMIT :limit OFFSET :currentIndex
            """)
    Flux<Comment> getCommentRequest(String postId, int limit, int currentIndex);

    @Query(value = """
                SELECT *
                FROM comments
                WHERE post_id = :postId 
                  AND parent_id = :parentId
                ORDER BY created_at DESC
                LIMIT :limit OFFSET :currentIndex
            """)
    Flux<Comment> getChildComment(String postId, String parentId, int limit, int currentIndex);

    Mono<Void> deleteAllByParentId(String parentId);

    Flux<Comment> findAllByUserId(String userId);
}
