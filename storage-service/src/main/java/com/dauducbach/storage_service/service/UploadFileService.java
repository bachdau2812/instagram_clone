package com.dauducbach.storage_service.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.EagerTransformation;
import com.cloudinary.utils.ObjectUtils;
import com.dauducbach.storage_service.dto.request.DeleteMediaRequest;
import com.dauducbach.storage_service.dto.request.UploadFileRequest;
import com.dauducbach.storage_service.entity.Media;
import com.dauducbach.storage_service.repository.MediaRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.File;
import java.io.IOException;
import java.text.Normalizer;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j

public class UploadFileService {

    Cloudinary cloudinary;
    MediaRepository mediaRepository;
    R2dbcEntityTemplate r2dbcEntityTemplate;

    public Mono<List<String>> uploadMedia(UploadFileRequest request) {
        return Flux.fromIterable(request.getMedias())
                .flatMap(filePart -> Mono.fromCallable(() -> File.createTempFile("upload-", filePart.filename()))
                                .subscribeOn(Schedulers.boundedElastic())
                                .flatMap(tempFile -> filePart.transferTo(tempFile.toPath())
                                        .then(Mono.defer(() -> {
                                            try {
                                                String suffix = getFileSuffix(filePart);
                                                String fileName = filePart.filename().substring(0, filePart.filename().indexOf("."));
                                                Map map = null;

                                                if (suffix.equals("jpg") || suffix.equals("jpeg") || suffix.equals("png") ||
                                                        suffix.equals("gif") || suffix.equals("bmp") || suffix.equals("tiff") ||
                                                        suffix.equals("webp") || suffix.equals("heif") || suffix.equals("heic") ||
                                                        suffix.equals("svg")) {
                                                    map = Map.of(
                                                            "resource_type", "image",
                                                            "folder", "instagram",
                                                            "owner_id", request.getOwner()
                                                    );
                                                } else if (suffix.equals("mp4") || suffix.equals("avi") || suffix.equals("mov") ||
                                                        suffix.equals("mkv") || suffix.equals("webm") || suffix.equals("flv") ||
                                                        suffix.equals("wmv") || suffix.equals("m4v") || suffix.equals("3gp")) {
                                                    map = Map.of(
                                                            "resource_type", "video",
                                                            "folder", "instagram",
                                                            "eager", Arrays.asList(
                                                                    new EagerTransformation().width(467).height(582).crop("fit").audioCodec("aac"),
                                                                    new EagerTransformation().width(648).height(864).crop("fit").audioCodec("aac")
                                                            ),
                                                            "eager_async", true
//                                                            "eager_notification_url", "https://mysite.example.com/notify_endpoint"
                                                    );
                                                } else if (suffix.equals("mp3") || suffix.equals("m4a")) {

                                                    map = Map.of(
                                                            "resource_type", "video",
                                                            "folder", "instagram",
                                                            "public_id", normalizeFileName(fileName)
                                                    );
                                                }

                                                Map uploadResult = cloudinary.uploader().upload(tempFile, map);

                                                tempFile.delete();

                                                var media = Media.builder()
                                                        .assetId((String) uploadResult.get("asset_id"))
                                                        .publicId((String) uploadResult.get("public_id"))
                                                        .width((int) uploadResult.get("width"))
                                                        .height((int) uploadResult.get("height"))
                                                        .format((String) uploadResult.get("format"))
                                                        .resourceType((String) uploadResult.get("resource_type"))
                                                        .createAt(Instant.now())
                                                        .bytes((int) uploadResult.get("bytes"))
                                                        .url((String) uploadResult.get("url"))
                                                        .secureUrl((String) uploadResult.get("secure_url"))
                                                        .ownerId(request.getOwner())
                                                        .version(String.valueOf(uploadResult.get("version")))
                                                        .versionId((String) uploadResult.get("version_id"))
                                                        .displayName(fileName)
                                                        .isAvatar(request.isAvatar())
                                                        .build();

                                                log.info("Media: {}", media);

                                                return r2dbcEntityTemplate.insert(Media.class).using(media)
                                                        .then(Mono.just(media.getSecureUrl()));
                                            } catch (IOException e) {
                                                throw new RuntimeException(e);
                                            }
                                        }))
                                )
                ).collectList();
    }

    private String getFileSuffix(FilePart filePart) {
        String filename = filePart.filename();
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex > 0 && dotIndex < filename.length() - 1) {
            return filename.substring(dotIndex + 1).toLowerCase();
        }
        return "";
    }

    private String normalizeFileName(String name) {
        return Normalizer.normalize(name, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")   // bỏ dấu tiếng Việt
                .replaceAll("[^a-zA-Z0-9-_]", "_"); // thay ký tự đặc biệt bằng _
    }

    public Mono<Void> deleteMedia(DeleteMediaRequest request) {
        return findAllByPublicIds(request.getPublicIds())
                .switchIfEmpty(Mono.error(new RuntimeException("Empty")))
                .flatMap(media -> {
                    Map map = null;

                    if (media.getResourceType().equals("video")) {
                        map = Map.of(
                                "resource_type", "video",
                                "invalidate", true
                        );
                    } else if (media.getResourceType().equals("image")) {
                        map = ObjectUtils.emptyMap();
                    }

                    try {
                        return mediaRepository.delete(media)
                                .then(Mono.just(cloudinary.uploader().destroy(media.getPublicId(), map)));
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                })
                .then();
    }

    private Flux<Media> findAllByPublicIds(List<String> publicIds) {
        Criteria criteria = Criteria.where("public_id").in(publicIds);
        Query query = Query.query(criteria);

        return r2dbcEntityTemplate.select(query, Media.class);
    }

    public Mono<Void> deleteByOwnerId(String ownerId) {
        return r2dbcEntityTemplate.select(
                Query.query(Criteria.where("owner_id").is(ownerId)),
                Media.class
        )
                .map(Media::getPublicId)
                .collectList()
                .flatMap(publicIds -> deleteMedia(DeleteMediaRequest.builder().publicIds(publicIds).build()));
    }
}
