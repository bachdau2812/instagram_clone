package com.dauducbach.profile_service.service;

import com.dauducbach.event.profile_operation.ProfileOperationFailEvent;
import com.dauducbach.event.profile_operation.ProfileOperationSuccessEvent;
import com.dauducbach.event.profile_operation.ProfileOperationCommand;
import com.dauducbach.profile_service.entity.Block;
import com.dauducbach.profile_service.entity.Follow;
import com.dauducbach.profile_service.repository.BlockRepository;
import com.dauducbach.profile_service.repository.FollowRepository;
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

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j

public class ProfileOperationHandleService {
    R2dbcEntityTemplate r2dbcEntityTemplate;
    FollowRepository followRepository;
    BlockRepository blockRepository;
    KafkaSender<String, Object> kafkaSender;

    @KafkaListener(topics = "profile_user_connect")
    public void profileOperationHandle(@Payload ProfileOperationCommand command) {
        log.info("Process operation");
        Mono.defer(() -> {
                    switch (command.type()) {
                        case SEND_FOLLOW_REQUEST -> {
                            return sendFollowRequest(command);
                        }
                        case ACCEPT_FOLLOW_REQUEST -> {
                            return acceptFollowRequest(command);
                        }
                        case UNFOLLOW_REQUEST -> {
                            return unfollowRequest(command);
                        }
                        case BLOCK_REQUEST -> {
                            return blockRequest(command);
                        }
                        case UNBLOCK_REQUEST -> {
                            return unblockRequest(command);
                        }
                        default -> {
                            return Mono.empty();
                        }
                    }
                })
                .flatMap(result -> {
                    // Event thành công
                    var successEvent = new ProfileOperationSuccessEvent(
                            command.type(),
                            command.sourceId(),
                            command.targetId()
                    );

                    log.info("Operation complete");

                    ProducerRecord<String, Object> producer = new ProducerRecord<>("profile_user_connect_success", successEvent);
                    SenderRecord<String, Object, String> senderRecord = SenderRecord.create(producer, "Complete");
                    return kafkaSender.send(Mono.just(senderRecord)).then();
                })
                .onErrorResume(err -> {
                    // Event lỗi cho Saga
                    var failEvent = new ProfileOperationFailEvent(
                            command.type(),
                            command.sourceId(),
                            command.targetId()
                    );

                    log.info("Operation fail: {}", err.getMessage());

                    ProducerRecord<String, Object> producer = new ProducerRecord<>("profile_user_connect_fail", failEvent);
                    SenderRecord<String, Object, String> senderRecord = SenderRecord.create(producer, "Complete");
                    return kafkaSender.send(Mono.just(senderRecord)).then();
                })
                .subscribe();
    }

    public Mono<String> sendFollowRequest(ProfileOperationCommand record){
        return followRepository.existsByFollowerIdAndFollowingId(record.targetId(), record.sourceId())
                        .flatMap(isExists -> {
                            if (isExists) {
                                return Mono.error(new RuntimeException("Exists request"));
                            }

                            var follow = Follow.builder()
                                    .followerId(record.sourceId())
                                    .followingId(record.targetId())
                                    .createAt(Instant.now())
                                    .status("PENDING")
                                    .build();

                            return r2dbcEntityTemplate.insert(Follow.class).using(follow);
                        }
                        )
                .then(Mono.just("Complete"));
    }

    public Mono<String> acceptFollowRequest(ProfileOperationCommand event) {
        return followRepository.findByFollowerIdAndFollowingId(event.targetId(), event.sourceId())
                .flatMap(follow -> {
                    follow.setStatus("CONFIRM");
                    return followRepository.save(follow);
                })
                .onErrorResume(throwable -> Mono.error(new RuntimeException("Error: " + throwable.getMessage())))
                .then(Mono.just("Complete"));
    }

    public Mono<String> unfollowRequest(ProfileOperationCommand event) {
        return followRepository.deleteByFollowerIdAndFollowingId(event.sourceId(), event.targetId())
                .onErrorResume(throwable -> Mono.error(new RuntimeException("Error: " + throwable.getMessage())))
                .then(Mono.just("Complete"));
    }

    public Mono<String> blockRequest(ProfileOperationCommand event) {
        var block = Block.builder()
                .blockerId(event.sourceId())
                .blockingId(event.targetId())
                .build();

        return r2dbcEntityTemplate.insert(Block.class).using(block)
                .onErrorResume(throwable -> Mono.error(new RuntimeException("Error: " + throwable.getMessage())))
                .then(Mono.just("Complete"));
    }

    public Mono<String> unblockRequest(ProfileOperationCommand event) {
        return blockRepository.deleteByBlockerIdAndBlockingId(event.sourceId(), event.targetId())
                .onErrorResume(throwable -> Mono.error(new RuntimeException("Error: " + throwable.getMessage())))
                .then(Mono.just("Complete"));
    }
}
