package com.dauducbach.post_service.service;

import com.dauducbach.event.post_creation.SavePostCommand;
import com.dauducbach.event.post_creation.SavePostSuccessEvent;
import com.dauducbach.post_service.entity.Post;
import com.dauducbach.post_service.entity.Tag;
import com.dauducbach.post_service.repository.PostRepository;
import com.dauducbach.post_service.repository.TagRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderRecord;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j

public class SavePostCommandService {
    R2dbcEntityTemplate r2dbcEntityTemplate;
    PostRepository postRepository;
    TagRepository tagRepository;
    KafkaSender<String, Object> kafkaSender;

    @KafkaListener(topics = "save_post_command")
    public void savePost(@Payload SavePostCommand command) {
        var post = Post.builder()
                .id(command.getPostId())
                .userId(command.getUserId())
                .content(command.getContent())
                .createAt(Instant.now())
                .updateAt(Instant.now())
                .build();

        r2dbcEntityTemplate.insert(Post.class).using(post)
                .then(
                        Flux.fromIterable(command.getTags())
                                .flatMap(t -> {
                                    var tag = Tag.builder()
                                            .id(UUID.randomUUID().toString())
                                            .postId(command.getPostId())
                                            .content(t)
                                            .build();

                                    return r2dbcEntityTemplate.insert(Tag.class).using(tag);
                                })
                                .then()
                )
                .then(Mono.defer(() -> {
                    var event = SavePostSuccessEvent.builder()
                            .postId(post.getId())
                            .userId(post.getUserId())
                            .content(post.getContent())
                            .tags(command.getTags())
                            .createAt(post.getCreateAt())
                            .updateAt(post.getUpdateAt())
                            .build();

                    ProducerRecord<String, Object> producerRecord = new ProducerRecord<>("save_post_success_event", event);
                    SenderRecord<String, Object, String> senderRecord = SenderRecord.create(producerRecord, "post creation");

                    return kafkaSender.send(Mono.just(senderRecord))
                            .then();
                }))
                .onErrorResume(err -> {
                    ProducerRecord<String, Object> producerRecord = new ProducerRecord<>("save_post_fail_event", post.getId());
                    SenderRecord<String, Object, String> senderRecord = SenderRecord.create(producerRecord, "post creation");

                    return kafkaSender.send(Mono.just(senderRecord))
                            .then();
                })
                .subscribe();
    }

    @KafkaListener(topics = "rollback_post_service_command")
    public void rollback(@Payload String postId) {
        postRepository.deleteById(postId)
                .then(r2dbcEntityTemplate.delete(Tag.class)
                        .matching(Query.query(Criteria.where("post_id").is(postId)))
                        .all())
                .subscribe();
    }
}
