package com.dauducbach.profile_service.controller;

import com.dauducbach.profile_service.dto.request.GetFollowingListResponse;
import com.dauducbach.profile_service.dto.request.GetListFollowerResponse;
import com.dauducbach.profile_service.dto.response.UserInfo;
import com.dauducbach.profile_service.dto.request.ProfileEditRequest;
import com.dauducbach.profile_service.dto.request.UserBasicInfoRequest;
import com.dauducbach.profile_service.dto.response.UserInfoLikeResponse;
import com.dauducbach.profile_service.service.GetProfileDataService;
import com.dauducbach.profile_service.service.ProfileService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)

public class ProfileController {
    ProfileService profileService;
    GetProfileDataService getProfileDataService;

    @PostMapping("/edit")
    Mono<Void> editProfile(@RequestBody ProfileEditRequest request) {
        return profileService.editProfile(request);
    }

    @PostMapping("/get-basic-info")
    Mono<List<UserInfo>> getBasicInfo(@RequestBody UserBasicInfoRequest request) {
        return profileService.getBasicInfo(request);
    }

    @PostMapping("/get-like-info")
    Mono<List<UserInfoLikeResponse>> getLikeInfo(@RequestBody UserBasicInfoRequest request) {
        return profileService.getInfoLikeResponse(request);
    }

    @GetMapping("/get-follower")
    Mono<GetListFollowerResponse> getFollower(@RequestParam String userId) {
        return getProfileDataService.getFollowerList(userId);
    }

    @GetMapping("/get-following")
    Mono<GetFollowingListResponse> getFollowing(@RequestParam String userId) {
        return getProfileDataService.getFollowingList(userId);
    }
}
