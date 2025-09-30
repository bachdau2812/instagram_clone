package com.dauducbach.storage_service.controller;

import com.dauducbach.storage_service.dto.request.DeleteMediaRequest;
import com.dauducbach.storage_service.dto.request.UploadFileRequest;
import com.dauducbach.storage_service.service.UploadFileService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)

public class UploadFileController {
    UploadFileService uploadFileService;

    @PostMapping(value = "/upload-media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    Mono<List<String>> uploadMedia(@RequestPart List<FilePart> files,@RequestPart String ownerId, @RequestPart String isAvatar) {
        var uploadFileRequest = UploadFileRequest.builder()
                .owner(ownerId)
                .medias(files)
                .isAvatar(isAvatar.equals("true"))
                .build();

        return uploadFileService.uploadMedia(uploadFileRequest);
    }

    @PostMapping(value = "/delete-media")
    Mono<Void> deleteMedia(@RequestBody DeleteMediaRequest request) {
        return uploadFileService.deleteMedia(request);
    }
}