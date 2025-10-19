package com.dauducbach.storage_service.controller;

import com.dauducbach.storage_service.dto.request.GetAvatarRequest;
import com.dauducbach.storage_service.dto.request.GetFileRequest;
import com.dauducbach.storage_service.dto.response.GetAvatarResponse;
import com.dauducbach.storage_service.entity.Media;
import com.dauducbach.storage_service.service.GetFileService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)

@RequestMapping("/get")
public class GetFileController {
    GetFileService getFileService;

    @GetMapping("/avt-original")
    Mono<String> getOriginalAvatar(@RequestParam String ownerId) {
        return getFileService.getAvatarOriginalUrl(ownerId);
    }

    @GetMapping("/avt-profile")
    Mono<String> getProfileAvatar(@RequestParam String ownerId) {
        return getFileService.getAvatarProfileUrl(ownerId);
    }

    @GetMapping("/avt-feed")
    Mono<String> getOriginalFeed(@RequestParam String ownerId) {
        return getFileService.getAvatarFeedUrl(ownerId);
    }

    @PostMapping("/medias")
    Mono<List<String>> getMedias(@RequestBody GetFileRequest request) {
        return getFileService.getFile(request)
                .collectList();
    }

    @GetMapping("/audio")
    Mono<String> getAudio(@RequestParam String displayName) {
        return getFileService.getMediaByDisplayName(displayName);
    }

    @GetMapping("/all")
    Mono<List<Media>> getAll() {
        return getFileService.getAll();
    }

    @PostMapping("/list-avt")
    Mono<GetAvatarResponse> getListAvatar(@RequestBody GetAvatarRequest request) {
        return getFileService.getAvatarOfListUser(request);
    }
}
