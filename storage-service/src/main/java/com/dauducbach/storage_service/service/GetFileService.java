package com.dauducbach.storage_service.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.Transformation;
import com.dauducbach.storage_service.constant.MediaVariant;
import com.dauducbach.storage_service.dto.request.GetAvatarRequest;
import com.dauducbach.storage_service.dto.request.GetFileRequest;
import com.dauducbach.storage_service.dto.response.GetAvatarResponse;
import com.dauducbach.storage_service.entity.Media;
import com.dauducbach.storage_service.repository.MediaRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j

public class GetFileService {
    MediaRepository mediaRepository;
    Cloudinary cloudinary;
    R2dbcEntityTemplate r2dbcEntityTemplate;

    public Mono<String> getAvatarOriginalUrl(String ownerId) {
        return mediaRepository.findByOwnerId(ownerId)
                .map(Media::getSecureUrl);
    }

    public Mono<String> getAvatarProfileUrl(String ownerId) {
        return mediaRepository.findByOwnerId(ownerId)
                .map(media -> cloudinary.url()
                        .transformation(new Transformation<>()
                                .rawTransformation(MediaVariant.IMAGE_AVATAR_PROFILE.getTransformation())
                        )
                        .generate(media.getPublicId())
                );
    }

    public Mono<String> getAvatarFeedUrl(String ownerId) {
        return mediaRepository.findByOwnerId(ownerId)
                .map(media -> cloudinary.url()
                        .transformation(new Transformation<>()
                                .rawTransformation(MediaVariant.IMAGE_AVATAR_FEED.getTransformation())
                        )
                        .generate(media.getPublicId())
                );
    }

    public Mono<String> getMediaFeed(Media media) {
        return Mono.fromCallable(() -> {
            if (media.getResourceType().equals("video")) {
                return cloudinary.url()
                        .resourceType(media.getResourceType())
                        .transformation(new Transformation<>()
                                .rawTransformation(MediaVariant.VIDEO_FEED.getTransformation())
                        )
                        .version(media.getVersion())
                        .generate(media.getPublicId());
            } else {
                return cloudinary.url()
                        .resourceType(media.getResourceType())
                        .transformation(new Transformation<>()
                                .rawTransformation(MediaVariant.IMAGE_FEED.getTransformation())
                        )
                        .version(media.getVersion())
                        .generate(media.getPublicId());
            }
        });
    }

    public Mono<String> getMediaProfile(Media media) {
        return Mono.fromCallable(() -> {
            if (media.getResourceType().equals("video")) {
                return cloudinary.url()
                        .resourceType(media.getResourceType())
                        .transformation(new Transformation<>()
                                .rawTransformation(MediaVariant.VIDEO_PROFILE.getTransformation())
                        )
                        .version(media.getVersion())
                        .generate(media.getPublicId());
            } else {
                return cloudinary.url()
                        .resourceType(media.getResourceType())
                        .transformation(new Transformation<>()
                                .rawTransformation(MediaVariant.IMAGE_PROFILE.getTransformation())
                        )
                        .version(media.getVersion())
                        .generate(media.getPublicId());
            }
        });
    }

    public Mono<String> getMediaProfileFocus(Media media) {
        return Mono.fromCallable(() -> {
            if (media.getResourceType().equals("video")) {
                return cloudinary.url()
                        .resourceType(media.getResourceType())
                        .transformation(new Transformation<>()
                                .rawTransformation(MediaVariant.VIDEO_PROFILE_FOCUS.getTransformation())
                        )
                        .version(media.getVersion())
                        .generate(media.getPublicId());
            } else {
                return cloudinary.url()
                        .resourceType(media.getResourceType())
                        .transformation(new Transformation<>()
                                .rawTransformation(MediaVariant.IMAGE_PROFILE_FOCUS.getTransformation())
                        )
                        .version(media.getVersion())
                        .generate(media.getPublicId());
            }
        });
    }

    public Flux<String> getFile(GetFileRequest request) {
        return findAllByOwnerId(request.getOwnerId())
                .flatMap(media -> {
                    switch (request.getMediaType()) {
                        case FEED -> {
                            return getMediaFeed(media);
                        }

                        case PROFILE -> {
                            return getMediaProfile(media);
                        }

                        case PROFILE_FOCUS -> {
                            return getMediaProfileFocus(media);
                        }
                        default -> {
                            return Mono.empty();
                        }
                    }
                });
    }

    public Mono<String> getMediaByDisplayName(String displayName) {
        log.info("Display name: {}", displayName);
        return mediaRepository.findByDisplayName(displayName)
                .map(Media::getSecureUrl);
    }

    public Flux<Media> findAllByOwnerId(String ownerId) {
        Criteria criteria = Criteria.where("owner_id").is(ownerId);
        Query query = Query.query(criteria);

        return r2dbcEntityTemplate.select(query, Media.class);
    }

    public Mono<List<Media>> getAll() {
        return mediaRepository.findAll()
                .collectList();
    }

    public Mono<GetAvatarResponse> getAvatarOfListUser(GetAvatarRequest request) {
        return Flux.fromIterable(request.getUserIds())
                .flatMap(userId -> mediaRepository.findByOwnerIdAndIsAvatar(userId, true))
                .collectMap(
                        Media::getOwnerId,
                        Media::getSecureUrl
                )
                .map(stringStringMap -> GetAvatarResponse.builder().userAvatarUrls(stringStringMap).build());
    }
}
