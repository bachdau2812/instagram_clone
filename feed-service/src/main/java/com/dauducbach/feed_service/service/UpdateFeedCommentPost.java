package com.dauducbach.feed_service.service;

import co.elastic.clients.json.JsonData;
import com.dauducbach.event.comment_post.CommentRollback;
import com.dauducbach.event.comment_post.CommentUpdateFeedCommand;
import com.dauducbach.event.comment_post.CommentUpdateFeedSuccessEvent;
import com.dauducbach.feed_service.dto.response.PostIndex;
import com.dauducbach.feed_service.entity.PostInfo;
import com.dauducbach.feed_service.entity.UserFeed;
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

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j

public class UpdateFeedCommentPost {
    UserFeedRepository userFeedRepository;
    KafkaSender<String, Object> kafkaSender;
    ReactiveElasticsearchTemplate elasticsearchTemplate;

    @KafkaListener(topics = "comment_update_feed_command")
    public void updateFeed(@Payload CommentUpdateFeedCommand command) {
        userFeedRepository.findById(command.actorId())
                .flatMap(userFeed -> {
                    IndexCoordinates index = IndexCoordinates.of("post_aggregate");

                    Mono<PostIndex> getPostIndex = elasticsearchTemplate.get(command.postId(), PostIndex.class, index);
                    Mono<List<PostInfo>> getPostOfPostOf = userFeedRepository.findById(command.postOf())
                            .map(UserFeed::getFeedList)
                            .map(postInfos -> postInfos.stream()
                                    .sorted((o1, o2) -> Long.compare(o2.getCreateAt(), o1.getCreateAt()))
                                    .limit(10)
                                    .toList()
                            );

                    Mono<List<PostInfo>> getPostOfParentIdOf = userFeedRepository.findById(command.parentIdOf())
                            .map(UserFeed::getFeedList)
                            .map(postInfos -> postInfos.stream()
                                    .sorted((o1, o2) -> Long.compare(o2.getCreateAt(), o1.getCreateAt()))
                                    .limit(10)
                                    .toList()
                            );

                    return Mono.zip(
                            getPostIndex,
                            getPostOfPostOf,
                            getPostOfParentIdOf
                    )
                            .flatMap(tuple -> {
                                var postIndex = tuple.getT1();
                                var feedFromPostOf = tuple.getT2();
                                var feedFromParentIdOf = tuple.getT3();

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
                                        .withPageable(Pageable.ofSize(10))
                                        .build();

                                Mono<List<PostInfo>> similarPostsMono = elasticsearchTemplate
                                        .search(nativeQuery, PostIndex.class, index)
                                        .map(hit -> PostInfo.builder()
                                                .postId(hit.getContent().getId())
                                                .createAt(hit.getContent().getCreateAt())
                                                .score(String.valueOf(hit.getScore()))
                                                .build())
                                        .collectList();

                                return similarPostsMono.flatMap(similarPosts -> {
                                    // Gộp & loại trùng
                                    List<PostInfo> merged = Stream.of(similarPosts, feedFromPostOf, feedFromParentIdOf)
                                            .flatMap(Collection::stream)
                                            .collect(Collectors.toMap(
                                                    PostInfo::getPostId,
                                                    Function.identity(),
                                                    (a, b) -> a
                                            ))
                                            .values()
                                            .stream()
                                            .sorted(Comparator.comparing(PostInfo::getCreateAt).reversed())
                                            .limit(15)
                                            .toList();

                                    merged.forEach(post -> {
                                        post.setRead(false);
                                        post.setTrending(false);

                                        userFeed.getFeedList().removeIf(p -> p.getPostId().equals(post.getPostId()));

                                        userFeed.getFeedList().add(post);
                                    });
                                    return userFeedRepository.save(userFeed);
                                });
                            });
                })
                .then(Mono.defer(() -> {
                    var event = new CommentUpdateFeedSuccessEvent(command.commentId());

                    ProducerRecord<String, Object> producerRecord = new ProducerRecord<>("comment_update_feed_success_event", command.actorId(), event);
                    SenderRecord<String, Object, String> senderRecord = SenderRecord.create(producerRecord, "comment post");

                    return kafkaSender.send(Mono.just(senderRecord))
                            .then();
                }))
                .onErrorResume(err -> {
                    log.info("Error update feed: {}", err.getMessage());

                    var event = new CommentRollback(command.commentId());

                    ProducerRecord<String, Object> producerRecord = new ProducerRecord<>("comment_update_feed_fail_event", command.actorId(), event);
                    SenderRecord<String, Object, String> senderRecord = SenderRecord.create(producerRecord, "comment post");

                    return kafkaSender.send(Mono.just(senderRecord))
                            .then();
                })
                .subscribe();
    }
}
