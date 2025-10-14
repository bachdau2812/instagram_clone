package com.dauducbach.fanout_service.service;

import com.dauducbach.event.UpdatePostCommand;
import com.dauducbach.event.comment_post.CommentPostIndexCommand;
import com.dauducbach.event.comment_post.CommentPostIndexSuccessEvent;
import com.dauducbach.event.comment_post.CommentRollback;
import com.dauducbach.event.like_post.LikePostIndexCommand;
import com.dauducbach.event.like_post.LikePostIndexSuccessEvent;
import com.dauducbach.event.like_post.LikePostSuccessEvent;
import com.dauducbach.event.like_post.LikeRollback;
import com.dauducbach.event.post_creation.SavePostIndexCommand;
import com.dauducbach.event.post_creation.SavePostIndexSuccessEvent;
import com.dauducbach.fanout_service.entity.PostIndex;
import com.dauducbach.fanout_service.repository.PostIndexRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderRecord;

import java.util.ArrayList;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j

public class UpdatePostElasticsearch {
    PostIndexRepository postIndexRepository;
    GetVectorEmbedding getVectorEmbedding;
    KafkaSender<String, Object> kafkaSender;

    // Post create
    @KafkaListener(topics = "save_post_index_command")
    public void savePostIndex(@Payload SavePostIndexCommand command) {
        var postIndex = PostIndex.builder()
                .id(command.getPostId())
                .userId(command.getUserId())
                .content(command.getContent())
                .tags(command.getTags())
                .likedId(new ArrayList<>())
                .commentedId(new ArrayList<>())
                .createAt(command.getCreateAt())
                .updateAt(command.getUpdateAt())
                .build();

        StringBuilder sb = new StringBuilder();
        sb.append(postIndex.getContent());
        command.getTags().forEach(sb::append);

        getVectorEmbedding.getEmbedding(sb.toString())
                .flatMap(floats -> {
                    postIndex.setEmbedding(floats);

                    return postIndexRepository.save(postIndex);
                })
                .flatMap(res -> {
                    var event = SavePostIndexSuccessEvent.builder()
                            .postId(command.getPostId())
                            .userId(command.getUserId())
                            .createAt(postIndex.getCreateAt())
                            .build();

                    ProducerRecord<String, Object> producerRecord = new ProducerRecord<>("save_post_index_success_event", event);
                    SenderRecord<String, Object, String> senderRecord = SenderRecord.create(producerRecord, "post creation");

                    return kafkaSender.send(Mono.just(senderRecord))
                            .then();
                })
                .onErrorResume(err -> {
                    ProducerRecord<String, Object> producerRecord = new ProducerRecord<>("save_post_index_success_event", command.getPostId());
                    SenderRecord<String, Object, String> senderRecord = SenderRecord.create(producerRecord, "post creation");

                    return kafkaSender.send(Mono.just(senderRecord))
                            .then();
                })
                .subscribe();
    }

    @KafkaListener(topics = "rollback_post_index_command")
    public void rollback(@Payload String postId) {
        postIndexRepository.deleteById(postId)
                .subscribe();
    }

    // Like post

    @KafkaListener(topics = "like_post_index_command")
    public void likePost(@Payload LikePostIndexCommand command) {
        postIndexRepository.findById(command.getPostId())
                .flatMap(postIndex -> {
                    if (postIndex.getLikedId().contains(command.getActorId())) {
                        log.info("Exists");
                        postIndex.getLikedId().remove(command.getActorId());

                        return postIndexRepository.save(postIndex);
                    } else {
                        log.info("Not Exists");
                        postIndex.getLikedId().add(command.getActorId());

                        return postIndexRepository.save(postIndex)
                                .then(Mono.defer(() -> {
                                    var event = LikePostIndexSuccessEvent.builder()
                                            .actorId(command.getActorId())
                                            .targetId(command.getTargetId())
                                            .postId(command.getPostId())
                                            .build();

                                    ProducerRecord<String, Object> producerRecord = new ProducerRecord<>("like_post_index_success_event", event);
                                    SenderRecord<String, Object, String> senderRecord = SenderRecord.create(producerRecord, "post operation");

                                    return kafkaSender.send(Mono.just(senderRecord))
                                            .then();
                                }))
                                .onErrorResume(err -> {
                                    log.info("Error like post index: {}", err.getMessage());

                                    var likeRollback = LikeRollback.builder()
                                            .postId(command.getPostId())
                                            .actorId(command.getActorId())
                                            .build();

                                    ProducerRecord<String, Object> producerRecord = new ProducerRecord<>("like_post_index_fail_event", likeRollback);
                                    SenderRecord<String, Object, String> senderRecord = SenderRecord.create(producerRecord, "post operation");

                                    return kafkaSender.send(Mono.just(senderRecord))
                                            .then();
                                });
                    }
                })
                .subscribe();
    }

    @KafkaListener(topics = "rollback_like_post_index")
    public void rollbackLikePost (@Payload LikeRollback likeRollback) {
        postIndexRepository.findById(likeRollback.getPostId())
                .flatMap(postIndex -> {
                    postIndex.getLikedId().remove(likeRollback.getActorId());

                    return postIndexRepository.save(postIndex);
                })
                .subscribe();
    }

    // comment post
    @KafkaListener(topics = "comment_post_index_command")
    public void commentPostIndex(@Payload CommentPostIndexCommand command) {
        postIndexRepository.findById(command.postId())
                .flatMap(postIndex -> {
                    if (!postIndex.getCommentedId().contains(command.actorId())) {
                        postIndex.getCommentedId().add(command.actorId());
                    }

                    var event = new CommentPostIndexSuccessEvent(
                            command.commentId(),
                            command.postId(),
                            command.actorId()
                    );

                    ProducerRecord<String, Object> producerRecord = new ProducerRecord<>("comment_post_index_success_event", event);
                    SenderRecord<String, Object, String> senderRecord = SenderRecord.create(producerRecord, "post operation");

                    return kafkaSender.send(Mono.just(senderRecord))
                            .then();
                })
                .onErrorResume(err -> {
                    log.info("Error comment post index: {}", err.getMessage());
                    var event = new CommentRollback(command.commentId());
                    ProducerRecord<String, Object> producerRecord = new ProducerRecord<>("comment_post_index_fail_event", event);
                    SenderRecord<String, Object, String> senderRecord = SenderRecord.create(producerRecord, "post operation");

                    return kafkaSender.send(Mono.just(senderRecord))
                            .then();
                })
                .subscribe();
    }

    @KafkaListener(topics = "delete_post_command")
    public void deletePost(@Payload String postId) {
        postIndexRepository.deleteById(postId)
                .subscribe();
    }

    @KafkaListener(topics = "update_post_command")
    public void updatePost(@Payload UpdatePostCommand command) {
        postIndexRepository.findById(command.getPostId())
                .flatMap(postIndex -> {
                    postIndex.setContent(command.getContent());
                    postIndex.setTags(command.getTags());

                    StringBuilder sb = new StringBuilder();
                    sb.append(postIndex.getContent());
                    command.getTags().forEach(sb::append);

                    return getVectorEmbedding.getEmbedding(sb.toString())
                            .flatMap(floats -> {
                                postIndex.setEmbedding(floats);

                                return postIndexRepository.save(postIndex);
                            });
                })
                .subscribe();
    }
}
