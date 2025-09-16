package com.dauducbach.profile_service.service;

import com.dauducbach.event.ProfileCreationEvent;
import com.dauducbach.profile_service.entity.Profile;
import com.dauducbach.profile_service.mapper.ProfileMapper;
import com.dauducbach.profile_service.repository.ProfileRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j

public class ProfileService {
    R2dbcEntityTemplate r2dbcEntityTemplate;
    ProfileRepository profileRepository;
    ProfileMapper profileMapper;

    @KafkaListener(topics = "profile_creation_event", groupId = "profile-service")
    public void createProfile(@Payload ProfileCreationEvent event) {

        var profile = profileMapper.toProfile(event);

        profile.setId(event.getUserId());
        profile.setSex("NONE");
        profile.setBio("");

        r2dbcEntityTemplate.insert(Profile.class).using(profile).subscribe();
    }

    public Mono<Void> setPhoneNumber(String phoneNumber, String id) {
        return profileRepository.findById(id)
                .map(profile -> {
                    profile.setPhoneNumber(phoneNumber);
                    return profile;
                })
                .then();
    }

    public Mono<Void> setCity(String city, String id) {
        return profileRepository.findById(id)
                .map(profile -> {
                    profile.setCity(city);
                    return profile;
                })
                .then();
    }

    public Mono<Void> setJob(String job, String id) {
        return profileRepository.findById(id)
                .map(profile -> {
                    profile.setJob(job);
                    return profile;
                })
                .then();
    }

    public Mono<Void> setProfileMode(boolean isPublic, String id) {
        return profileRepository.findById(id)
                .map(profile -> {
                    profile.setPublic(isPublic);
                    return profile;
                })
                .then();
    }

    public Mono<Void> setSex(String sex, String id) {
        return profileRepository.findById(id)
                .map(profile -> {
                    profile.setSex(sex);
                    return profile;
                })
                .then();
    }

    public Mono<Void> setBio(String bio, String id) {
        return profileRepository.findById(id)
                .map(profile -> {
                    profile.setBio(bio);
                    return profile;
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
