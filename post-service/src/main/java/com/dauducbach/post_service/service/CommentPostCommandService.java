package com.dauducbach.post_service.service;

import com.dauducbach.event.comment_post.CommentPostCommand;
import com.dauducbach.event.comment_post.CommentPostSuccessEvent;
import com.dauducbach.event.comment_post.CommentRollback;
import com.dauducbach.post_service.entity.Comment;
import com.dauducbach.post_service.entity.Post;
import com.dauducbach.post_service.repository.CommentRepository;
import com.dauducbach.post_service.repository.PostRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderRecord;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j

public class CommentPostCommandService {
    CommentRepository commentRepository;
    KafkaSender<String, Object> kafkaSender;
    PostRepository postRepository;
    R2dbcEntityTemplate r2dbcEntityTemplate;

    @KafkaListener(topics = "comment_post_command")
    public void handleComment(@Payload CommentPostCommand command) {
        var comment = Comment.builder()
                .id(UUID.randomUUID().toString())
                .postId(command.postId())
                .parentId(command.parentId())
                .content(command.content())
                .userId(command.actorId())
                .createAt(Instant.now())
                .updateAt(Instant.now())
                .build();

        postRepository.findById(command.postId())
                .map(Post::getUserId)
                .flatMap(postOf -> {
                    if (command.parentId() == null) {
                        var event = new CommentPostSuccessEvent(
                                comment.getId(),
                                comment.getUserId(),
                                comment.getPostId(),
                                postOf,
                                null,
                                null,
                                comment.getContent()
                        );

                        return r2dbcEntityTemplate.insert(Comment.class).using(comment)
                                .then(Mono.defer(() -> {
                                    ProducerRecord<String, Object> producerRecord = new ProducerRecord<>("comment_post_success_event", comment.getUserId(), event);
                                    SenderRecord<String, Object, String> senderRecord = SenderRecord.create(producerRecord, "comment post");

                                    return kafkaSender.send(Mono.just(senderRecord))
                                            .then();
                                }));
                    } else {
                        return commentRepository.findById(command.parentId())
                                .map(Comment::getUserId)
                                .flatMap(parentIdOf -> {
                                    var event = new CommentPostSuccessEvent(
                                            comment.getId(),
                                            comment.getUserId(),
                                            comment.getPostId(),
                                            postOf,
                                            command.parentId(),
                                            parentIdOf,
                                            comment.getContent()
                                    );

                                    return r2dbcEntityTemplate.insert(Comment.class).using(comment)
                                            .then(Mono.defer(() -> {
                                                ProducerRecord<String, Object> producerRecord = new ProducerRecord<>("comment_post_success_event", comment.getUserId(), event);
                                                SenderRecord<String, Object, String> senderRecord = SenderRecord.create(producerRecord, "comment post");

                                                return kafkaSender.send(Mono.just(senderRecord))
                                                        .then();
                                            }));
                                });
                    }
                })
                .onErrorResume(err -> {
                    log.info("Error comment post: {}", err.getMessage());

                    ProducerRecord<String, Object> producerRecord = new ProducerRecord<>("comment_post_fail_event", comment.getUserId(), comment.getId());
                    SenderRecord<String, Object, String> senderRecord = SenderRecord.create(producerRecord, "comment post");

                    return kafkaSender.send(Mono.just(senderRecord))
                            .then();
                })
                .subscribe();
    }

    @KafkaListener(topics = "rollback_comment_post")
    public void rollback(@Payload CommentRollback commentRollback) {
        commentRepository.deleteById(commentRollback.commentId())
                .subscribe();
    }
}
