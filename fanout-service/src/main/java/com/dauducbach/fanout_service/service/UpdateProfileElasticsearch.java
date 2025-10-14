package com.dauducbach.fanout_service.service;

import com.dauducbach.event.ProfileCreationEvent;
import com.dauducbach.event.ProfileEditEvent;
import com.dauducbach.event.profile_operation.ProfileIndexUserConnectCommand;
import com.dauducbach.event.profile_operation.ProfileIndexUserConnectSuccessEvent;
import com.dauducbach.event.profile_operation.ProfileOperationRollback;
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
    private final GetVectorEmbedding getVectorEmbedding;

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

        String text = String.format(
                "User profile information: " +
                        "User ID: %s, Username: %s, Display Name: %s, " +
                        "Phone Number: %s, Email: %s, " +
                        "City: %s, Job: %s, Date of Birth: %s",
                event.getUserId(),
                event.getUsername(),
                event.getDisplayName(),
                event.getPhoneNumber(),
                event.getEmail(),
                event.getCity(),
                event.getJob(),
                event.getDob()
        );

        getVectorEmbedding.getEmbedding(text).flatMap(embedding -> {
            profileIndex.setEmbedding(embedding);

            return  profileIndexRepository.save(profileIndex)
                    .doOnSuccess(saved -> log.info("Profile indexed: {}", saved.getId()))
                    .doOnError(err -> log.error("Failed to index profile", err));
        }).subscribe();
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

    @KafkaListener(topics = "profile_index_operation_command")
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

                    ProducerRecord<String, Object> producerRecord = new ProducerRecord<>("profile_index_operation_success_event", event);
                    SenderRecord<String, Object, String> senderRecord = SenderRecord.create(producerRecord, "profile operator");

                    return kafkaSender.send(Mono.just(senderRecord))
                            .then();
                })
                .onErrorResume(err -> {
                    var event = new ProfileOperationRollback(
                            command.type(),
                            command.sourceId(),
                            command.targetId()
                    );

                    log.info("Fail: {}", err.getMessage());

                    ProducerRecord<String, Object> producerRecord = new ProducerRecord<>("profile_index_operation_fail_event", event);
                    SenderRecord<String, Object, String> senderRecord = SenderRecord.create(producerRecord, "profile operator");

                    return kafkaSender.send(Mono.just(senderRecord))
                            .then();
                })
                .subscribe();
    }

    public Mono<String> sendFollowRequest(ProfileIndexUserConnectCommand event) {
        return profileIndexRepository.findById(event.sourceId())
                .flatMap(sourceProfile -> {
                    if (!sourceProfile.getFollowingRequestList().contains(event.targetId())) {
                        sourceProfile.getFollowingRequestList().add(event.targetId());
                    }
                    return profileIndexRepository.findById(event.targetId())
                            .flatMap(targetProfile -> {
                                targetProfile.getFollowerRequestList().add(event.sourceId());
                                return profileIndexRepository.save(targetProfile);
                            })
                            .then(profileIndexRepository.save(sourceProfile));
                })
                .thenReturn("SUCCESS")
                .onErrorResume(e -> Mono.error(new RuntimeException("Error: " + e.getMessage())));
    }

    public Mono<String> acceptFollowRequest(ProfileIndexUserConnectCommand event) {
        return profileIndexRepository.findById(event.sourceId()) // người A chấp nhận
                .flatMap(sourceProfile -> {
                    sourceProfile.getFollowerRequestList().remove(event.targetId());
                    sourceProfile.getFollowerList().add(event.targetId());

                    return profileIndexRepository.findById(event.targetId()) // người B được follow
                            .flatMap(targetProfile -> {
                                targetProfile.getFollowingRequestList().remove(event.sourceId());
                                targetProfile.getFollowingList().add(event.sourceId());
                                return profileIndexRepository.save(targetProfile);
                            })
                            .then(profileIndexRepository.save(sourceProfile));
                })
                .thenReturn("SUCCESS")
                .onErrorResume(e -> Mono.error(new RuntimeException("Error: " + e.getMessage())));
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
                .thenReturn("SUCCESS")
                .onErrorResume(e -> Mono.error(new RuntimeException("Error: " + e.getMessage())));
    }


    public Mono<String> blockRequest(ProfileIndexUserConnectCommand event) {
        return profileIndexRepository.findById(event.sourceId())
                .flatMap(sourceProfile -> {
                    sourceProfile.getBlockList().add(event.targetId());
                    return profileIndexRepository.save(sourceProfile);
                })
                .thenReturn("SUCCESS")
                .onErrorResume(e -> Mono.error(new RuntimeException("Error: " + e.getMessage())));
    }


    public Mono<String> unblockRequest(ProfileIndexUserConnectCommand event) {
        return profileIndexRepository.findById(event.sourceId())
                .flatMap(sourceProfile -> {
                    sourceProfile.getBlockList().remove(event.targetId());
                    return profileIndexRepository.save(sourceProfile);
                })
                .thenReturn("SUCCESS")
                .onErrorResume(e -> Mono.error(new RuntimeException("Error: " + e.getMessage())));
    }

    // rollback
    public Mono<String> rollback(ProfileOperationRollback event) {
        return switch (event.type()) {
            case SEND_FOLLOW_REQUEST -> rollbackSendFollow(event);
            case ACCEPT_FOLLOW_REQUEST -> rollbackAcceptFollow(event);
            case UNFOLLOW_REQUEST -> rollbackUnfollow(event);
            case BLOCK_REQUEST -> rollbackBlock(event);
            case UNBLOCK_REQUEST -> rollbackUnblock(event);
            default -> Mono.just("IGNORE");
        };
    }

    private Mono<String> rollbackSendFollow(ProfileOperationRollback event) {
        return profileIndexRepository.findById(event.sourceId())
                .flatMap(sourceProfile -> {
                    sourceProfile.getFollowingRequestList().remove(event.targetId());
                    return profileIndexRepository.findById(event.targetId())
                            .flatMap(targetProfile -> {
                                targetProfile.getFollowerRequestList().remove(event.sourceId());
                                return profileIndexRepository.save(targetProfile);
                            })
                            .then(profileIndexRepository.save(sourceProfile));
                })
                .thenReturn("ROLLBACK_SEND_FOLLOW_SUCCESS")
                .onErrorResume(e -> Mono.error(new RuntimeException("Rollback send follow fail: " + e.getMessage())));
    }

    private Mono<String> rollbackAcceptFollow(ProfileOperationRollback event) {
        return profileIndexRepository.findById(event.sourceId())
                .flatMap(sourceProfile -> {
                    sourceProfile.getFollowerList().remove(event.targetId());
                    sourceProfile.getFollowerRequestList().add(event.targetId());
                    return profileIndexRepository.findById(event.targetId())
                            .flatMap(targetProfile -> {
                                targetProfile.getFollowingList().remove(event.sourceId());
                                targetProfile.getFollowingRequestList().add(event.sourceId());
                                return profileIndexRepository.save(targetProfile);
                            })
                            .then(profileIndexRepository.save(sourceProfile));
                })
                .thenReturn("ROLLBACK_ACCEPT_FOLLOW_SUCCESS")
                .onErrorResume(e -> Mono.error(new RuntimeException("Rollback accept follow fail: " + e.getMessage())));
    }

    private Mono<String> rollbackUnfollow(ProfileOperationRollback event) {
        return profileIndexRepository.findById(event.sourceId())
                .flatMap(sourceProfile -> {
                    if (!sourceProfile.getFollowingList().contains(event.targetId())) {
                        sourceProfile.getFollowingList().add(event.targetId());
                    }

                    return profileIndexRepository.findById(event.targetId())
                            .flatMap(targetProfile -> {
                                if (!targetProfile.getFollowerList().contains(event.sourceId())) {
                                    targetProfile.getFollowerList().add(event.sourceId());
                                }
                                return profileIndexRepository.save(targetProfile);
                            })
                            .then(profileIndexRepository.save(sourceProfile));
                })
                .thenReturn("ROLLBACK_UNFOLLOW_SUCCESS")
                .onErrorResume(e -> Mono.error(new RuntimeException("Rollback unfollow fail: " + e.getMessage())));
    }


    private Mono<String> rollbackBlock(ProfileOperationRollback event) {
        return profileIndexRepository.findById(event.sourceId())
                .flatMap(sourceProfile -> {
                    sourceProfile.getBlockList().remove(event.targetId());
                    return profileIndexRepository.save(sourceProfile);
                })
                .thenReturn("ROLLBACK_BLOCK_SUCCESS")
                .onErrorResume(e -> Mono.error(new RuntimeException("Rollback block fail: " + e.getMessage())));
    }

    private Mono<String> rollbackUnblock(ProfileOperationRollback event) {
        return profileIndexRepository.findById(event.sourceId())
                .flatMap(sourceProfile -> {
                    if (!sourceProfile.getBlockList().contains(event.targetId())) {
                        sourceProfile.getBlockList().add(event.targetId());
                    }
                    return profileIndexRepository.save(sourceProfile);
                })
                .thenReturn("ROLLBACK_UNBLOCK_SUCCESS")
                .onErrorResume(e -> Mono.error(new RuntimeException("Rollback unblock fail: " + e.getMessage())));
    }


}
