package com.dauducbach.notification_service.controller;

import com.dauducbach.notification_service.dto.request.EmailConfigRequest;
import com.dauducbach.notification_service.dto.request.PushConfigRequest;
import com.dauducbach.notification_service.entity.EmailConfig;
import com.dauducbach.notification_service.entity.PushConfig;
import com.dauducbach.notification_service.service.NotificationConfigService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j

@RequestMapping("/config")
public class NotificationConfigController {
    NotificationConfigService notificationConfigService;

//    @GetMapping("/set-all")
//    Mono<Void> setAll(@RequestParam String userId) {
//        return notificationConfigService.setConfigWhenUserCreate(userId);
//    }

    @PostMapping("/set-push")
    Mono<Void> setPush(@RequestBody PushConfigRequest request) {
        return notificationConfigService.setPushConfig(request);
    }

    @PostMapping("/set-email")
    Mono<Void> setEmail(@RequestBody EmailConfigRequest request) {
        return notificationConfigService.setEmailConfig(request);
    }

    @GetMapping("/get-email")
    Flux<EmailConfig> getEmailConfigOfUser(@RequestParam String userId) {
        return notificationConfigService.getEmailConfig(userId);
    }

    @GetMapping("/get-push")
    Flux<PushConfig> getPushConfigOfUser(@RequestParam String userId) {
        return notificationConfigService.getPushConfig(userId);
    }
}
