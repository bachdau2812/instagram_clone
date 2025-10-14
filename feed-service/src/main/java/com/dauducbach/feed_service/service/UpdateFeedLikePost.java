package com.dauducbach.feed_service.service;

import co.elastic.clients.json.JsonData;
import com.dauducbach.event.like_post.LikeRollback;
import com.dauducbach.event.like_post.UpdateFeedCommand;
import com.dauducbach.event.like_post.UpdateFeedSuccessEvent;
import com.dauducbach.feed_service.dto.response.PostIndex;
import com.dauducbach.feed_service.entity.PostInfo;
import com.dauducbach.feed_service.repository.UserFeedRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.client.elc.ReactiveElasticsearchTemplate;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderRecord;

import java.util.Map;


@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j

public class UpdateFeedLikePost {
    UserFeedRepository userFeedRepository;
    ReactiveElasticsearchTemplate reactiveElasticsearchTemplate;
    KafkaSender<String, Object> kafkaSender;

    @KafkaListener(topics = "update_feed_command")
    public void updateFeed(@Payload UpdateFeedCommand command) {
        update(command).subscribe();
    }

    public Mono<Void> update(UpdateFeedCommand command) {
        return userFeedRepository.findById(command.getActorId())
                .flatMap(userFeed -> {
                    IndexCoordinates index = IndexCoordinates.of("post_aggregate");

                    return reactiveElasticsearchTemplate.get(command.getPostId(), PostIndex.class, index);
                })
                .flatMap(postIndex -> {
                    NativeQuery nativeQuery = NativeQuery.builder()
                            .withQuery(q -> q
                                    .scriptScore(ss -> ss
                                            .query(innerQuery -> innerQuery
                                                    .matchAll(m -> m)
                                            )
                                            .script(script -> script
                                                    .source("cosineSimilarity(params.query_vector, 'embedding') + 1.0")
                                                    .params(Map.of("query_vector", JsonData.of(postIndex.getEmbedding()))
                                                    )
                                            )
                                    )
                            )
                            .withPageable(Pageable.ofSize(5))
                            .build();

                    IndexCoordinates index = IndexCoordinates.of("post_aggregate");

                    return reactiveElasticsearchTemplate.search(nativeQuery, PostIndex.class, index)
                            .map(searchHit -> {
                                PostIndex source = searchHit.getContent();
                                return PostInfo.builder()
                                        .postId(source.getId())
                                        .createAt(source.getCreateAt())
                                        .score(String.valueOf(searchHit.getScore()))
                                        .build();
                            })
                            .collectList()
                            .flatMap(postInfos ->
                                    userFeedRepository.findById(command.getActorId())
                                            .flatMap(userFeed -> {
                                                postInfos.forEach(postInfo -> userFeed.getFeedList().add(postInfo));

                                                return userFeedRepository.save(userFeed);
                                            })
                            );

                })
                .then(Mono.defer(() -> {
                    var event = UpdateFeedSuccessEvent.builder()
                            .actorId(command.getActorId())
                            .targetId(command.getTargetId())
                            .postId(command.getPostId())
                            .build();

                    log.info("Event: {}", event);

                    ProducerRecord<String, Object> producerRecord = new ProducerRecord<>("update_feed_success_event", event);
                    SenderRecord<String, Object, String> senderRecord = SenderRecord.create(producerRecord, "profile operator");

                    return kafkaSender.send(Mono.just(senderRecord))
                            .then();
                }))
                .onErrorResume(err -> {
                    log.info("Error undate feed: {}", err.getMessage());

                    var event = LikeRollback.builder()
                            .actorId(command.getActorId())
                            .postId(command.getPostId())
                            .build();

                    ProducerRecord<String, Object> producerRecord = new ProducerRecord<>("update_feed_fail_event", event);
                    SenderRecord<String, Object, String> senderRecord = SenderRecord.create(producerRecord, "profile operator");

                    return kafkaSender.send(Mono.just(senderRecord))
                            .then();
                });
    }
}
