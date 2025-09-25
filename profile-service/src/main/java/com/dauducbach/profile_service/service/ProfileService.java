package com.dauducbach.profile_service.service;

import com.dauducbach.event.ProfileCreationEvent;
import com.dauducbach.event.ProfileEditEvent;
import com.dauducbach.event.UploadFileEvent;
import com.dauducbach.profile_service.dto.request.ProfileEditRequest;
import com.dauducbach.profile_service.entity.Profile;
import com.dauducbach.profile_service.mapper.ProfileMapper;
import com.dauducbach.profile_service.repository.ProfileRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderRecord;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j

public class ProfileService {
    R2dbcEntityTemplate r2dbcEntityTemplate;
    ProfileRepository profileRepository;
    ProfileMapper profileMapper;
    KafkaSender<String, Object> kafkaSender;

    @KafkaListener(topics = "profile_creation_event", groupId = "profile-service")
    public void createProfile(@Payload ProfileCreationEvent event) {

        var profile = profileMapper.toProfile(event);

        profile.setId(event.getUserId());
        profile.setSex("NONE");
        profile.setBio("");

        r2dbcEntityTemplate.insert(Profile.class).using(profile).subscribe();
    }

    public Mono<Void> editProfile(ProfileEditRequest request) {
        return profileRepository.findById(request.getProfileId())
                .flatMap(profile -> {
                    // 1. Build event
                    var event = ProfileEditEvent.builder()
                            .profileId(profile.getId())
                            .fieldName(request.getFieldName())
                            .value(request.getValue())
                            .build();

                    ProducerRecord<String, Object> producerRecord =
                            new ProducerRecord<>("profile_edit_event", event);

                    SenderRecord<String, Object, String> senderRecord =
                            SenderRecord.create(producerRecord, "Edit Profile");

                    // 2. Update profile theo fieldName
                    Mono<Profile> updatedProfileMono = switch (request.getFieldName()) {
                        case "displayName" -> {
                            profile.setDisplayName(request.getValue());
                            yield profileRepository.save(profile);
                        }
                        case "city" -> {
                            profile.setCity(request.getValue());
                            yield profileRepository.save(profile);
                        }
                        case "job" -> {
                            profile.setJob(request.getValue());
                            yield profileRepository.save(profile);
                        }
                        case "sex" -> {
                            profile.setSex(request.getValue());
                            yield profileRepository.save(profile);
                        }
                        case "bio" -> {
                            profile.setBio(request.getValue());
                            yield profileRepository.save(profile);
                        }
                        case "dob" -> {
                            profile.setDob(LocalDate.parse(request.getValue()));
                            yield profileRepository.save(profile);
                        }
                        default -> Mono.empty();
                    };

                    // 3. Kết hợp: gửi Kafka xong rồi lưu DB (hoặc ngược lại tuỳ yêu cầu)
                    return kafkaSender.send(Mono.just(senderRecord))
                            .then(updatedProfileMono);
                })
                .then();
    }

    public Mono<Void> deleteProfile(String id) {
        return profileRepository.deleteById(id);
    }

    public Mono<Void> deleteSllProfile() {
        return profileRepository.deleteAll();
    }
}
