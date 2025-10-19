package com.dauducbach.notification_service.service;

import com.dauducbach.notification_service.dto.request.GetAvatarRequest;
import com.dauducbach.notification_service.dto.request.UserBasicInfoRequest;
import com.dauducbach.notification_service.dto.request.UserInfo;
import com.dauducbach.notification_service.dto.response.GetAvatarResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor

public class GetUserInfo {
    private final WebClient webClient;

    public Mono<List<UserInfo>> getUserInfo(List<String> userInfoRequest) {
        return webClient.post()
                .uri("http://localhost:8081/profile/get-basic-info")
                .bodyValue(UserBasicInfoRequest.builder()
                        .userId(userInfoRequest)
                        .build()
                )
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<UserInfo>>() {});
    }

    public Mono<Map<String, String>> getListAvtOfListUser(List<String> userInfoRequest) {
        return webClient.post()
                .uri("http://localhost:8084/storage/get/list-avt")
                .bodyValue(GetAvatarRequest.builder()
                        .userIds(userInfoRequest)
                        .build()
                )
                .retrieve()
                .bodyToMono(GetAvatarResponse.class)
                .map(GetAvatarResponse::getUserAvatarUrls);
    }
}
