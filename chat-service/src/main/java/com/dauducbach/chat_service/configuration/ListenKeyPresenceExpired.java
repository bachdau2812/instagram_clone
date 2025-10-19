package com.dauducbach.chat_service.configuration;

import com.dauducbach.chat_service.service.EncodingUtils;
import com.dauducbach.chat_service.service.PresenceService;
import com.dauducbach.chat_service.service.WebSocketSessionRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.connection.ReactiveSubscription;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;

@Configuration
@RequiredArgsConstructor
@Slf4j

public class ListenKeyPresenceExpired {
    private final PresenceService presenceService;
    private final WebSocketSessionRegistry registry;
    private final ReactiveRedisMessageListenerContainer container;


    @EventListener(ApplicationReadyEvent.class)
    public void startListen() {
        var topic = new ChannelTopic("__keyevent@0__:expired");

        container.receive(topic)
                .map(ReactiveSubscription.Message::getMessage)
                .filter(key -> key.startsWith("presence_onl:"))
                .flatMap(key -> {
                    String userId = key.replace("presence_onl:", "");
                    log.info("1. chuan bi xoa: {}", userId);
                    registry.removeSession(userId);
                    return presenceService.setOffline(userId)
                            .then(presenceService.notifyOffline(userId))
                            .doOnSuccess(unused -> log.info("2. Xoa thanh cong: {}", userId));
                })
                .doOnSubscribe(s -> log.info("🔔 Redis expired listener started"))
                .subscribe();
    }
}
