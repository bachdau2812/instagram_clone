package com.dauducbach.profile_service.service;

import com.dauducbach.profile_service.dto.request.FollowItemResponse;
import com.dauducbach.profile_service.dto.request.GetFollowingListResponse;
import com.dauducbach.profile_service.dto.request.GetListFollowerResponse;
import com.dauducbach.profile_service.repository.BlockRepository;
import com.dauducbach.profile_service.repository.FollowRepository;
import com.dauducbach.profile_service.repository.ProfileRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j

public class GetProfileDataService {
    ProfileRepository profileRepository;
    FollowRepository followRepository;
    BlockRepository blockRepository;
    WebClient webClient;

    public Mono<GetListFollowerResponse> getFollowerList(String userId) {
        return followRepository.findAllByFollowingId(userId)
                .flatMap(follow -> webClient.get()
                        .uri("http://localhost:8084/storage/get/avt-feed?ownerId=" + follow.getFollowerId())
                        .retrieve()
                        .bodyToMono(String.class)
                        .flatMap(avt -> profileRepository.findById(follow.getFollowerId())
                                .map(profile -> FollowItemResponse.builder()
                                        .userId(follow.getFollowerId())
                                        .displayName(profile.getDisplayName())
                                        .avtUrl(avt)
                                        .build()
                                )
                        )
                )
                .collectList()
                .map(followItemResponses -> GetListFollowerResponse.builder()
                        .userId(userId)
                        .followerList(followItemResponses)
                        .build());
    }

    public Mono<GetFollowingListResponse>  getFollowingList(String userId) {
        return followRepository.findAllByFollowerId(userId)
                .flatMap(follow -> webClient.get()
                        .uri("http://localhost:8084/storage/get/avt-feed?ownerId=" + follow.getFollowerId())
                        .retrieve()
                        .bodyToMono(String.class)
                        .flatMap(avt -> profileRepository.findById(follow.getFollowerId())
                                .map(profile -> FollowItemResponse.builder()
                                        .userId(follow.getFollowerId())
                                        .displayName(profile.getDisplayName())
                                        .avtUrl(avt)
                                        .build()
                                )
                        )
                )
                .collectList()
                .map(followItemResponses -> GetFollowingListResponse.builder()
                        .userId(userId)
                        .followingList(followItemResponses)
                        .build());
    }
}
