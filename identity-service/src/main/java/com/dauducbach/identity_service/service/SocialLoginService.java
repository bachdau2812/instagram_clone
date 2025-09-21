package com.dauducbach.identity_service.service;

import com.dauducbach.event.user_service.NotificationEvent;
import com.dauducbach.event.ProfileCreationEvent;
import com.dauducbach.event.SaveAvatarFromOauth2Event;
import com.dauducbach.identity_service.entity.User;
import com.dauducbach.identity_service.entity.UserRoles;
import com.dauducbach.identity_service.exception.AppException;
import com.dauducbach.identity_service.exception.ErrorCode;
import com.dauducbach.identity_service.repository.UserRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.userinfo.DefaultReactiveOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.ReactiveOAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderRecord;

import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j

public class SocialLoginService extends DefaultReactiveOAuth2UserService {
    R2dbcEntityTemplate r2dbcEntityTemplate;
    UserRepository userRepository;
    KafkaSender<String, Object> kafkaSender;
    PasswordEncoder passwordEncoder;

    @Override
    public Mono<OAuth2User> loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        ReactiveOAuth2UserService<OAuth2UserRequest, OAuth2User> oAuth2UserService = new DefaultReactiveOAuth2UserService();

        return oAuth2UserService.loadUser(userRequest)
                .flatMap(oAuth2User -> processOAuth2User(userRequest, oAuth2User))
                .onErrorResume(e -> {
                    log.error("Error processing OAuth2 user: {}", e.getMessage());
                    return Mono.error(new AppException(ErrorCode.AUTHENTICATION_FAILED));
                });
    }

    private Mono<OAuth2User> processOAuth2User(OAuth2UserRequest userRequest, OAuth2User oAuth2User) {
        String email = oAuth2User.getAttribute("email");
        String displayName = oAuth2User.getAttribute("name");
        String provider = userRequest.getClientRegistration().getRegistrationId();

        // Extract provider-specific information
        String providerId = extractProviderId(oAuth2User, provider);
        String avatarUrl = extractAvatarUrl(oAuth2User, provider);

        return userRepository.existsByProviderId(providerId)
                .flatMap(exists -> {
                    if (exists) {
                        return Mono.just(oAuth2User);
                    } else {
                        return userRepository.existsByEmail(email)
                                .flatMap(emailExists -> {
                                    if (emailExists) {
                                        return Mono.error(new AppException(ErrorCode.EMAIL_ALREADY_LINKED));
                                    } else {
                                        return createNewUser(email, displayName, provider, providerId, avatarUrl, oAuth2User);
                                    }
                                });
                    }
                });
    }

    private String extractProviderId(OAuth2User oAuth2User, String provider) {
        switch (provider) {
            case "google":
                return oAuth2User.getAttribute("sub"); // String
            case "facebook":
                return (String) Objects.requireNonNull(oAuth2User.getAttribute("id"));
            case "github":
                Integer githubId = oAuth2User.getAttribute("id");
                return githubId != null ? githubId.toString() : null;
            default:
                return null;
        }
    }

    private String extractAvatarUrl(OAuth2User oAuth2User, String provider) {
        switch (provider) {
            case "google":
                return oAuth2User.getAttribute("picture");
            case "facebook":
                Map<String, Object> picture = oAuth2User.getAttribute("picture");
                if (picture != null) {
                    Map<String, Object> data = (Map<String, Object>) picture.get("data");
                    if (data != null) {
                        return (String) data.get("url");
                    }
                }
                return null;
            case "github":
                return oAuth2User.getAttribute("avatar_url");
            default:
                return null;
        }
    }

    private Mono<OAuth2User> createNewUser(String email, String displayName, String provider,
                                           String providerId, String avatarUrl, OAuth2User oAuth2User) {
        User user = User.builder()
                .id(UUID.randomUUID().toString())
                .username(generateUsername(email))
                .password(passwordEncoder.encode("OAUTH2_USER"))
                .email(email)
                .provider(provider)
                .providerId(providerId)
                .build();

        // Create events
        ProfileCreationEvent profileCreationEvent = ProfileCreationEvent.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .displayName(displayName)
                .email(email)
                .build();

        var notificationEvent = NotificationEvent.builder()
                .subject("Chao mung den voi binh nguyen vo tan")
                .recipient(new String[]{user.getEmail()})
                .htmlContent("user_creation_event")
                .attachments(new ArrayList<>())
                .build();

        SaveAvatarFromOauth2Event saveAvatarFromOauth2Event = SaveAvatarFromOauth2Event.builder()
                .build();

        // Create Kafka records
        ProducerRecord<String, Object> profileRecord = new ProducerRecord<>("profile_creation_event", profileCreationEvent);
        SenderRecord<String, Object, String> profileSenderRecord = SenderRecord.create(profileRecord, "profile_creation");

        ProducerRecord<String, Object> notificationRecord = new ProducerRecord<>("user_creation_event", user.getId(), notificationEvent);
        SenderRecord<String, Object, String> notificationSenderRecord = SenderRecord.create(notificationRecord, "user_notification");

        ProducerRecord<String, Object> avatarRecord = new ProducerRecord<>("avatar_oauth2_event", user.getId(), saveAvatarFromOauth2Event);
        SenderRecord<String, Object, String> avatarSenderRecord = SenderRecord.create(avatarRecord, "avatar_save");

        // Save user and send events
        return r2dbcEntityTemplate.insert(User.class).using(user)
                .flatMap(savedUser ->
                        r2dbcEntityTemplate.insert(UserRoles.class)
                                .using(UserRoles.builder()
                                        .userId(savedUser.getId())
                                        .roleName("USER")
                                        .build()
                                )
                                .then(Mono.when(
                                        kafkaSender.send(Mono.just(profileSenderRecord)),
                                        kafkaSender.send(Mono.just(notificationSenderRecord)),
                                        kafkaSender.send(Mono.just(avatarSenderRecord))
                                ))
                                .doOnSuccess(unused -> log.info("Send Event complete"))
                                .thenReturn(oAuth2User)
                );
    }

    private String generateUsername(String email) {
        if (email != null && email.contains("@")) {
            return email.substring(0, email.indexOf("@"));
        }
        return "user_" + UUID.randomUUID().toString().substring(0, 8);
    }
}
