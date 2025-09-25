package com.dauducbach.fanout_service.service;

import com.dauducbach.event.ProfileCreationEvent;
import com.dauducbach.event.ProfileEditEvent;
import com.dauducbach.event.ProfileOperationEvent;
import com.dauducbach.fanout_service.dto.request.ProfileOperationRequest;
import com.dauducbach.fanout_service.entity.ProfileIndex;
import com.dauducbach.fanout_service.repository.ProfileIndexRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderRecord;

import java.util.ArrayList;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j

public class FanoutForProfileService {
    ProfileIndexRepository profileIndexRepository;
    KafkaSender<String, Object> kafkaSender;

    @KafkaListener(topics = "profile_creation_event")
    public void createProfileIndex(@Payload ProfileCreationEvent event) {
        var profileIndex = ProfileIndex.builder()
                .id(event.getUserId())
                .displayName(event.getDisplayName())
                .city(event.getCity())
                .job(event.getJob())
                .followerList(new ArrayList<>())
                .followerRequestList(new ArrayList<>())
                .followingRequestList(new ArrayList<>())
                .followingList(new ArrayList<>())
                .blockList(new ArrayList<>())
                .build();

        profileIndexRepository.save(profileIndex).subscribe();
    }

    @KafkaListener(topics = "profile_edit_event")
    public void editProfileIndex(@Payload ProfileEditEvent event) {
        profileIndexRepository.findById(event.getProfileId())
                .flatMap(profileIndex -> switch (event.getFieldName()) {
                    case "displayName" -> {
                        profileIndex.setDisplayName(event.getValue());
                        yield profileIndexRepository.save(profileIndex);
                    }
                    case "city" -> {
                        profileIndex.setCity(event.getValue());
                        yield profileIndexRepository.save(profileIndex);
                    }
                    case "job" -> {
                        profileIndex.setJob(event.getValue());
                        yield profileIndexRepository.save(profileIndex);
                    }
                    case "sex" -> {
                        profileIndex.setSex(event.getValue());
                        yield profileIndexRepository.save(profileIndex);
                    }
                    case "bio" -> {
                        profileIndex.setBio(event.getValue());
                        yield profileIndexRepository.save(profileIndex);
                    }
                    default -> Mono.empty();
                })
                .subscribe();
    }

    public Mono<Void> profileOperationHandle(ProfileOperationRequest request) {
        var event = ProfileOperationEvent.builder()
                .type(request.getType())
                .sourceId(request.getSourceId())
                .targetId(request.getTargetId())
                .build();

        ProducerRecord<String, Object> producerRecord = new ProducerRecord<>("profile_operation_event", event);
        SenderRecord<String, Object, String> senderRecord = SenderRecord.create(producerRecord, "profile operator");

        return kafkaSender.send(Mono.just(senderRecord))
                .then();
    }

    @KafkaListener(topics = "profile_operation_event")
    public void listenOperation(@Payload ProfileOperationEvent event) {
        switch (event.getType()){
            case SEND_FOLLOW_REQUEST -> sendFollowRequest(event).subscribe();
            case ACCEPT_FOLLOW_REQUEST -> acceptFollowRequest(event).subscribe();
            case UNFOLLOW_REQUEST -> unfollowRequest(event).subscribe();
            case BLOCK_REQUEST -> blockRequest(event).subscribe();
            case UNBLOCK_REQUEST -> unblockRequest(event).subscribe();
        }
    }

    public Mono<Void> sendFollowRequest(ProfileOperationEvent event) {
        return profileIndexRepository.findById(event.getSourceId())
                .flatMap(sourceProfile -> {
                    sourceProfile.getFollowingRequestList().add(event.getTargetId());

                    return profileIndexRepository.findById(event.getSourceId())
                            .flatMap(targetProfile -> {
                                targetProfile.getFollowerList().add(event.getTargetId());

                                return profileIndexRepository.save(targetProfile);
                            })
                            .then(profileIndexRepository.save(sourceProfile));
                })
                .then();
    }

    public Mono<Void> acceptFollowRequest(ProfileOperationEvent event) {
        return profileIndexRepository.findById(event.getSourceId())
                .flatMap(sourceProfile -> {
                    sourceProfile.getFollowerRequestList().remove(event.getTargetId());
                    sourceProfile.getFollowerList().add(event.getTargetId());

                    return profileIndexRepository.findById(event.getTargetId())
                            .flatMap(targetProfile -> {
                                targetProfile.getFollowingRequestList().remove(event.getSourceId());
                                targetProfile.getFollowingList().add(event.getSourceId());

                                return profileIndexRepository.save(targetProfile);
                            })
                            .then(profileIndexRepository.save(sourceProfile));
                })
                .then();
    }

    public Mono<Void> unfollowRequest(ProfileOperationEvent event) {
        return profileIndexRepository.findById(event.getSourceId())
                .flatMap(sourceProfile -> {
                    sourceProfile.getFollowingList().remove(event.getTargetId());

                    return profileIndexRepository.findById(event.getTargetId())
                            .flatMap(targetProfile -> {
                                targetProfile.getFollowerList().remove(event.getSourceId());

                                return profileIndexRepository.save(targetProfile);
                            })
                            .then(profileIndexRepository.save(sourceProfile));
                })
                .then();
    }

    public Mono<Void> blockRequest(ProfileOperationEvent event) {
        return profileIndexRepository.findById(event.getSourceId())
                .flatMap(sourceProfile -> {
                    sourceProfile.getBlockList().add(event.getTargetId());

                    return profileIndexRepository.save(sourceProfile);
                })
                .then();
    }

    public Mono<Void> unblockRequest(ProfileOperationEvent event) {
        return profileIndexRepository.findById(event.getSourceId())
                .flatMap(sourceProfile -> {
                    sourceProfile.getBlockList().remove(event.getTargetId());

                    return profileIndexRepository.save(sourceProfile);
                })
                .then();
    }


}
