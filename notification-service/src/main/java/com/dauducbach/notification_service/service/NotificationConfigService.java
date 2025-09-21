package com.dauducbach.notification_service.service;

import com.dauducbach.notification_service.constant.EmailTypeConfig;
import com.dauducbach.notification_service.constant.NotificationEvent;
import com.dauducbach.notification_service.dto.request.EmailConfigRequest;
import com.dauducbach.notification_service.dto.request.PushConfigRequest;
import com.dauducbach.notification_service.entity.EmailConfig;
import com.dauducbach.notification_service.entity.PushConfig;
import com.dauducbach.notification_service.repository.EmailConfigRepository;
import com.dauducbach.notification_service.repository.PushConfigRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j

public class NotificationConfigService {
    EmailConfigRepository emailConfigRepository;
    PushConfigRepository pushConfigRepository;
    R2dbcEntityTemplate r2dbcEntityTemplate;
    WebClient webClient;

    public Flux<EmailConfig> getEmailConfig(String userId) {
        return emailConfigRepository.findByUserId(userId);
    }

    public Flux<PushConfig> getPushConfig(String userId) {
        return pushConfigRepository.findByUserId(userId);
    }

    public Mono<Void> setConfigWhenUserCreate(String userId) {
        Mono<Void> setPush = Flux.fromIterable(Arrays.stream(NotificationEvent.values()).toList())
                .flatMap(notificationEvent -> {
                    var pushConfig = PushConfig.builder()
                            .id(UUID.randomUUID().toString())
                            .userId(userId)
                            .isEnable(true)
                            .notificationEvent(notificationEvent)
                            .build();

                    return r2dbcEntityTemplate.insert(PushConfig.class).using(pushConfig);
                })
                .then();

        Mono<Void> setEmail = Flux.fromIterable(Arrays.stream(EmailTypeConfig.values()).toList())
                .flatMap(emailTypeConfig -> {
                    var emailConfig = EmailConfig.builder()
                            .id(UUID.randomUUID().toString())
                            .userId(userId)
                            .isEnable(true)
                            .emailTypeConfig(emailTypeConfig)
                            .build();

                    return r2dbcEntityTemplate.insert(EmailConfig.class).using(emailConfig);
                })
                .then();

        return Mono.when(
                setPush,
                setEmail
        )
                .doOnError(throwable -> log.info("Error: {}", throwable.getMessage()))
                .then();
    }

    public Mono<Void> setPushConfig(PushConfigRequest request) {
        return pushConfigRepository.findByUserIdAndNotificationEvent(request.getUserId(), request.getNotificationEvent())
                .flatMap(pushConfig -> {
                    pushConfig.setEnable(request.isEnable());
                    return pushConfigRepository.save(pushConfig);
                })
                .then();
    }

    public Mono<Void> setEmailConfig(EmailConfigRequest request) {
        return emailConfigRepository.findByUserIdAndEmailTypeConfig(request.getUserId(), request.getEmailTypeConfig())
                .flatMap(emailConfig -> {
                    emailConfig.setEnable(request.isEnable());
                    return emailConfigRepository.save(emailConfig);
                })
                .then();
    }
}
