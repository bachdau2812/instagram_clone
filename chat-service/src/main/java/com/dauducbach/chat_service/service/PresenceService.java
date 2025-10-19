package com.dauducbach.chat_service.service;

import com.dauducbach.chat_service.dto.request.GetPresenceOfListUser;
import com.dauducbach.chat_service.dto.response.PresenceResponse;
import com.dauducbach.chat_service.dto.response.UserOffline;
import com.dauducbach.chat_service.dto.response.UserPresenceResponse;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.time.Instant;

@Service
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RequiredArgsConstructor
@Slf4j

public class PresenceService {
    private static final int TIMEOUT_ONLINE = 10;

    ReactiveRedisTemplate<String, String> redisString;
    ReactiveRedisTemplate<String, Long> redisLong;
    Sinks.Many<UserOffline> offlineSinks = Sinks.many().multicast().onBackpressureBuffer();

    public Mono<Void> setOnline(String userId) {
        return redisString.opsForValue()
                .set("presence_onl:" + userId, "online", Duration.ofMinutes(TIMEOUT_ONLINE))
                .then(redisLong.opsForValue()
                        .set("last_seen:" + userId, Instant.now().getEpochSecond())
                )
                .then();
    }

    public Mono<Void> setOffline(String userId) {
        return redisString.opsForValue()
                .delete("presence_onl:" + userId)
                .then();
    }

    public Mono<Long> getLastSeen(String userId) {
        return redisLong.opsForValue()
                .get("last_seen:" + userId);
    }

    public Mono<Void> refreshLastSeen(String userId) {
        return redisLong.opsForValue()
                .set("last_seen:" + userId, Instant.now().getEpochSecond())
                .then();
    }

    public Mono<String> getPresence(String userId) {
        return redisString.opsForValue()
                .get("presence_onl:" + userId)
                .defaultIfEmpty("offline");
    }

    public Mono<PresenceResponse> getPresenceOfListUser(GetPresenceOfListUser request) {
        return Flux.fromIterable(request.getUserIds())
                .flatMap(userId -> getPresence(userId)
                        .flatMap(presence -> {
                            log.info("Presence: {}", presence);
                            if (presence.equals("offline")) {
                                return redisLong.opsForValue().get("last_seen:" + userId)
                                        .flatMap(lastSeen -> Mono.just(UserPresenceResponse.builder()
                                                .userId(userId)
                                                .isOnline(false)
                                                .lastSeen(lastSeen)
                                                .build())
                                        );
                            } else {
                                return redisLong.opsForValue().get("last_seen:" + userId)
                                        .flatMap(lastSeen -> Mono.just(UserPresenceResponse.builder()
                                                .userId(userId)
                                                .isOnline(true)
                                                .lastSeen(lastSeen)
                                                .build())
                                        );
                            }
                        })
                )
                .collectList()
                .map(userPresenceResponses -> PresenceResponse.builder()
                        .responses(userPresenceResponses)
                        .build()
                );
    }

    public Mono<Void> notifyOffline(String userId) {
        return redisLong.opsForValue()
                .get("last_seen:" + userId)
                .defaultIfEmpty(Instant.now().getEpochSecond())
                .doOnNext(lastSeen -> {
                    var event = UserOffline.builder()
                            .userId(userId)
                            .status("offline")
                            .lastSeen(lastSeen)
                            .build();
                    Sinks.EmitResult result = offlineSinks.tryEmitNext(event);
                    if (result.isFailure()) {
                        log.info("Fail emit: {}", result);
                    } else {
                        log.info("Success emit: {}", result);
                    }
                })
                .then();
    }

    public Flux<UserOffline> offlineStream(String userId) {
        return offlineSinks.asFlux()
                .filter(userOffline -> userOffline.getUserId().equals(userId));
    }

}
