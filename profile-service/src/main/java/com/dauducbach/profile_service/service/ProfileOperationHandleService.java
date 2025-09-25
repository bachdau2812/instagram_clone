package com.dauducbach.profile_service.service;

import com.dauducbach.event.ProfileOperationEvent;
import com.dauducbach.profile_service.entity.Block;
import com.dauducbach.profile_service.entity.Follow;
import com.dauducbach.profile_service.repository.BlockRepository;
import com.dauducbach.profile_service.repository.FollowRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j

public class ProfileOperationHandleService {
    R2dbcEntityTemplate r2dbcEntityTemplate;
    FollowRepository followRepository;
    BlockRepository blockRepository;

    @KafkaListener(topics = "profile_operation_event")
    public void profileOperationHandle(@Payload ProfileOperationEvent event) {
        switch (event.getType()){
            case SEND_FOLLOW_REQUEST -> sendFollowRequest(event).subscribe();
            case ACCEPT_FOLLOW_REQUEST -> acceptFollowRequest(event).subscribe();
            case UNFOLLOW_REQUEST -> unfollowRequest(event).subscribe();
            case BLOCK_REQUEST -> blockRequest(event).subscribe();
            case UNBLOCK_REQUEST -> unblockRequest(event).subscribe();
        }
    }

    public Mono<Void> sendFollowRequest(ProfileOperationEvent event){
        return followRepository.existsByFollowerIdAndFollowingId(event.getTargetId(), event.getSourceId())
                        .flatMap(isExists -> {
                            var follow = Follow.builder()
                                    .followerId(event.getSourceId())
                                    .followingId(event.getTargetId())
                                    .createAt(Instant.now())
                                    .build();
                            if (isExists) {
                                follow.setStatus("CONFIRM");
                            } else {
                                follow.setStatus("PENDING");
                            }
                            return r2dbcEntityTemplate.insert(Follow.class).using(follow);
                        }
                        )
                .then();
    }

    public Mono<Void> acceptFollowRequest(ProfileOperationEvent event) {
        return followRepository.findByFollowerIdAndFollowingId(event.getTargetId(), event.getSourceId())
                .flatMap(follow -> {
                    follow.setStatus("CONFIRM");
                    return followRepository.save(follow);
                })
                .then();
    }

    public Mono<Void> unfollowRequest(ProfileOperationEvent event) {
        return followRepository.deleteByFollowerIdAndFollowingId(event.getSourceId(), event.getTargetId());
    }

    public Mono<Void> blockRequest(ProfileOperationEvent event) {
        var block = Block.builder()
                .blockerId(event.getSourceId())
                .blockingId(event.getTargetId())
                .build();

        return r2dbcEntityTemplate.insert(Block.class).using(block)
                .then();
    }

    public Mono<Void> unblockRequest(ProfileOperationEvent event) {
        return blockRepository.deleteByBlockerIdAndBlockingId(event.getSourceId(), event.getTargetId());
    }
}
