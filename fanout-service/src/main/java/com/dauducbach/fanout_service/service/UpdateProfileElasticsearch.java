package com.dauducbach.fanout_service.service;

import com.dauducbach.event.ProfileCreationEvent;
import com.dauducbach.event.ProfileEditEvent;
import com.dauducbach.event.profile_operation.ProfileIndexUserConnectCommand;
import com.dauducbach.event.profile_operation.ProfileIndexUserConnectFailEvent;
import com.dauducbach.event.profile_operation.ProfileIndexUserConnectSuccessEvent;
import com.dauducbach.fanout_service.entity.ProfileIndex;
import com.dauducbach.fanout_service.repository.ProfileIndexRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderRecord;

import java.util.ArrayList;

@Component
@RequiredArgsConstructor
@Slf4j

public class UpdateProfileElasticsearch {
    private final ProfileIndexRepository profileIndexRepository;
    private final KafkaSender<String, Object> kafkaSender;

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

        profileIndexRepository.save(profileIndex)
                .doOnSuccess(saved -> log.info("Profile indexed: {}", saved.getId()))
                .doOnError(err -> log.error("Failed to index profile", err))
                .subscribe();
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

    @KafkaListener(topics = "profile_index_user_connect")
    public void listenOperation(@Payload ProfileIndexUserConnectCommand command) {
        Mono.defer(() -> {
            switch (command.type()){
                case SEND_FOLLOW_REQUEST -> {
                    return sendFollowRequest(command);
                }
                case ACCEPT_FOLLOW_REQUEST -> {
                    return acceptFollowRequest(command);
                }
                case UNFOLLOW_REQUEST -> {
                    return unfollowRequest(command);
                }
                case BLOCK_REQUEST -> {
                    return blockRequest(command);
                }
                case UNBLOCK_REQUEST -> {
                    return unblockRequest(command);
                }
                default -> {
                    return Mono.empty();
                }
            }
        })
                .flatMap(result -> {
                    var event = new ProfileIndexUserConnectSuccessEvent(
                            command.type(),
                            command.sourceId(),
                            command.targetId()
                    );

                    log.info("Complete");

                    ProducerRecord<String, Object> producerRecord = new ProducerRecord<>("profile_index_user_connect_success", event);
                    SenderRecord<String, Object, String> senderRecord = SenderRecord.create(producerRecord, "profile operator");

                    return kafkaSender.send(Mono.just(senderRecord))
                            .then();
                })
                .onErrorResume(err -> {
                    var event = new ProfileIndexUserConnectFailEvent(
                            command.type(),
                            command.sourceId(),
                            command.targetId()
                    );

                    log.info("Fail: {}", err.getMessage());

                    ProducerRecord<String, Object> producerRecord = new ProducerRecord<>("profile_index_user_connect_fail", event);
                    SenderRecord<String, Object, String> senderRecord = SenderRecord.create(producerRecord, "profile operator");

                    return kafkaSender.send(Mono.just(senderRecord))
                            .then();
                })
                .subscribe();
    }

    public Mono<String> sendFollowRequest(ProfileIndexUserConnectCommand event) {
        return profileIndexRepository.findById(event.sourceId())
                .flatMap(sourceProfile -> {
                    sourceProfile.getFollowingRequestList().add(event.targetId());

                    return profileIndexRepository.findById(event.sourceId())
                            .flatMap(targetProfile -> {
                                targetProfile.getFollowerList().add(event.targetId());

                                return profileIndexRepository.save(targetProfile);
                            })
                            .then(profileIndexRepository.save(sourceProfile));
                })
                .onErrorResume(throwable -> Mono.error(new RuntimeException("Error: " + throwable.getMessage())))
                .then(Mono.just("SUCCESS"));
    }

    public Mono<String> acceptFollowRequest(ProfileIndexUserConnectCommand event) {
        return profileIndexRepository.findById(event.sourceId())
                .flatMap(sourceProfile -> {
                    sourceProfile.getFollowerRequestList().remove(event.targetId());
                    sourceProfile.getFollowerList().add(event.targetId());

                    return profileIndexRepository.findById(event.targetId())
                            .flatMap(targetProfile -> {
                                targetProfile.getFollowingRequestList().remove(event.sourceId());
                                targetProfile.getFollowingList().add(event.sourceId());

                                return profileIndexRepository.save(targetProfile);
                            })
                            .then(profileIndexRepository.save(sourceProfile));
                })
                .onErrorResume(throwable -> Mono.error(new RuntimeException("Error: " + throwable.getMessage())))
                .then(Mono.just("SUCCESS"));
    }

    public Mono<String> unfollowRequest(ProfileIndexUserConnectCommand event) {
        return profileIndexRepository.findById(event.sourceId())
                .flatMap(sourceProfile -> {
                    sourceProfile.getFollowingList().remove(event.targetId());

                    return profileIndexRepository.findById(event.targetId())
                            .flatMap(targetProfile -> {
                                targetProfile.getFollowerList().remove(event.sourceId());

                                return profileIndexRepository.save(targetProfile);
                            })
                            .then(profileIndexRepository.save(sourceProfile));
                })
                .onErrorResume(throwable -> Mono.error(new RuntimeException("Error: " + throwable.getMessage())))

                .then(Mono.just("SUCCESS"));
    }

    public Mono<String> blockRequest(ProfileIndexUserConnectCommand event) {
        return profileIndexRepository.findById(event.sourceId())
                .flatMap(sourceProfile -> {
                    sourceProfile.getBlockList().add(event.targetId());

                    return profileIndexRepository.save(sourceProfile);
                })
                .onErrorResume(throwable -> Mono.error(new RuntimeException("Error: " + throwable.getMessage())))

                .then(Mono.just("SUCCESS"));
    }

    public Mono<String> unblockRequest(ProfileIndexUserConnectCommand event) {
        return profileIndexRepository.findById(event.sourceId())
                .flatMap(sourceProfile -> {
                    sourceProfile.getBlockList().remove(event.targetId());

                    return profileIndexRepository.save(sourceProfile);
                })
                .onErrorResume(throwable -> Mono.error(new RuntimeException("Error: " + throwable.getMessage())))

                .then(Mono.just("SUCCESS"));
    }
}
