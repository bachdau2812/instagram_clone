package com.dauducbach.post_service.service;

import com.dauducbach.event.comment_post.CommentBroadSuccessEvent;
import com.dauducbach.event.comment_post.CommentBroadcastCommand;
import com.dauducbach.event.comment_post.CommentRollback;
import com.dauducbach.post_service.dto.response.CommentResponse;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderRecord;

@Service
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j

public class CommentBroadcastService {
    KafkaSender<String, Object> kafkaSender;
    Sinks.Many<CommentResponse> commentSinks = Sinks.many().multicast().onBackpressureBuffer();

    public CommentBroadcastService(KafkaSender<String, Object> kafkaSender) {
        this.kafkaSender = kafkaSender;
    }


    @KafkaListener(topics = "comment_broadcast_command")
    public void publishComment(@Payload CommentBroadcastCommand command) {
        log.info("Receive command: {}", command);
        var commentResponse = CommentResponse.builder()
                .commentId(command.commentId())
                .actorId(command.actorId())
                .parentId(command.parentId())
                .postOf(command.postOf())
                .content(command.content())
                .parentIdOf(command.parentIdOf())
                .postId(command.postId())
                .build();

        Sinks.EmitResult result = commentSinks.tryEmitNext(commentResponse);

        if (result.isFailure()) {
            log.info("Sink failure");
            var rollback = new CommentRollback(
                    command.commentId()
            );

            ProducerRecord<String, Object> producerRecord = new ProducerRecord<>("comment_broadcast_fail_event", command.actorId(), rollback);
            SenderRecord<String, Object, String> senderRecord = SenderRecord.create(producerRecord, "comment post");

            kafkaSender.send(Mono.just(senderRecord))
                    .subscribe();
        } else {
            log.info("Sink success");
            var successEvent = new CommentBroadSuccessEvent(
                    command.commentId()
            );

            ProducerRecord<String, Object> producerRecord = new ProducerRecord<>("comment_broadcast_success_event", command.actorId(), successEvent);
            SenderRecord<String, Object, String> senderRecord = SenderRecord.create(producerRecord, "comment post");

            kafkaSender.send(Mono.just(senderRecord))
                    .subscribe();
        }
    }

    public Flux<CommentResponse> streamCommentForPost(String postId) {
        return commentSinks.asFlux()
                .filter(comment -> comment.getPostId().equals(postId));
    }
}
