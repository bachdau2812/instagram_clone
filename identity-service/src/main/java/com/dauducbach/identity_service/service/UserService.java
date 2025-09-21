package com.dauducbach.identity_service.service;

import com.dauducbach.event.user_service.NotificationEvent;
import com.dauducbach.identity_service.constant.Provider;
import com.dauducbach.event.user_service.EmailVerifyEvent;
import com.dauducbach.event.user_service.ForgetPasswordEvent;
import com.dauducbach.event.user_service.NewPasswordEvent;
import com.dauducbach.identity_service.dto.request.EmailVerifyRequest;
import com.dauducbach.identity_service.dto.request.UserCreationRequest;
import com.dauducbach.identity_service.dto.request.VerifyForgetPasswordRequest;
import com.dauducbach.identity_service.dto.response.UserResponse;
import com.dauducbach.identity_service.entity.User;
import com.dauducbach.identity_service.exception.AppException;
import com.dauducbach.identity_service.exception.ErrorCode;
import com.dauducbach.identity_service.mapper.UserMapper;
import com.dauducbach.identity_service.repository.UserRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderRecord;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j

public class UserService {
    R2dbcEntityTemplate r2dbcEntityTemplate;
    UserRepository userRepository;
    UserMapper userMapper;
    PasswordEncoder passwordEncoder;
    ReactiveRedisTemplate<String, Object> reactiveRedisTemplate;
    KafkaSender<String, Object> kafkaSender;

    public Mono<Void> preRegister(UserCreationRequest request) {
        return Mono.zip(
                userRepository.existsByUsername(request.getUsername()),
                userRepository.existsByEmail(request.getEmail())
        )
                .flatMap(tuple -> {
                    boolean existsUsername = tuple.getT1();
                    boolean existsEmail = tuple.getT2();

                    if (existsUsername) {
                        return Mono.error(new AppException(ErrorCode.USERNAME_EXISTS));
                    }

                    if (existsEmail) {
                        return Mono.error(new AppException(ErrorCode.EMAIL_ALREADY_IN_USE));
                    }

                    // Luu tam thong tin nguoi dung va ma email xac thuc tuong ung vao redis truoc khi nguoi dung gui request verify
                    String userKey ="register:" + request.getEmail();
                    return reactiveRedisTemplate.opsForValue().set(userKey, request, Duration.ofMinutes(5))
                                    .then(sendCode(request.getEmail(), request.getUsername()));
                });
    }

    public Mono<Void> sendCode(String email, String username) {
        String code = RandomCode.generateRandomCode(8);
        String emailVerifyKey = "verify:" + email;
        return  reactiveRedisTemplate.opsForValue().set(emailVerifyKey, code, Duration.ofMinutes(5))
                .flatMap(saveComplete -> {
                    if (saveComplete) {
                        var emailVerifyEvent = EmailVerifyEvent.builder()
                                .username(username)
                                .email(email)
                                .code(code)
                                .build();

                        ProducerRecord<String, Object> producerRecord = new ProducerRecord<>("email_verify_event", email, emailVerifyEvent);
                        SenderRecord<String, Object, String> senderRecord = SenderRecord.create(producerRecord, "Gui email xac thuc email cho nguoi dung dang ky moi");
                        return kafkaSender.send(Mono.just(senderRecord))
                                .then();
                    }

                    return Mono.error(new AppException(ErrorCode.CODE_CREATION_FAILED));
                });

    }

    public Mono<UserResponse> emailVerifyAndCreateUser(EmailVerifyRequest request) {
        String userKey = "register:" + request.getEmail();
        String emailVerifyKey = "verify:" + request.getEmail();

        return Mono.zip(reactiveRedisTemplate.opsForValue().get(userKey),
                reactiveRedisTemplate.opsForValue().get(emailVerifyKey)
        ).flatMap(objects -> {
            var userRequest = (UserCreationRequest) objects.getT1();
            var code = String.valueOf(objects.getT2());

            // Kiem tra xem ma xac thuc da het han hay chua
            if (code == null) {
                return Mono.error(new AppException(ErrorCode.INVALID_REGISTRATION_INFO));
            }

            // Kiem tra xem ma xac thuc co chinh xac hay khong
            boolean isValid = code.equals(request.getCode());
            if (!isValid) {
                return Mono.error(new AppException(ErrorCode.INVALID_VERIFICATION_CODE));
            }

            // Logic tao user moi va gui event den cac service lien quan
            var user = User.builder()
                    .id(UUID.randomUUID().toString())
                    .username(userRequest.getUsername())
                    .password(passwordEncoder.encode(userRequest.getPassword()))
                    .email(userRequest.getEmail())
                    .provider(Provider.SYSTEM.toString())
                    .providerId(null)
                    .build();

            var profileCreationRequest = userMapper.toProfileCreationEvent(userRequest);
            profileCreationRequest.setDob(LocalDate.parse(userRequest.getDob()));

            profileCreationRequest.setUserId(user.getId());
            ProducerRecord<String, Object> producerRecord = new ProducerRecord<>("profile_creation_event", user.getId(), profileCreationRequest);
            SenderRecord<String, Object, String> senderRecord = SenderRecord.create(producerRecord, "Gui event de tao profile tuong ung voi nguoi dung");

            var notificationEvent = NotificationEvent.builder()
                    .subject("Chao mung den voi binh nguyen vo tan")
                    .recipient(new String[]{user.getEmail()})
                    .htmlContent("user_creation_event")
                    .attachments(new ArrayList<>())
                    .build();
            ProducerRecord<String, Object> producerRecord1 = new ProducerRecord<>("user_creation_event", user.getId(), notificationEvent);
            SenderRecord<String, Object, String> senderRecord1 = SenderRecord.create(producerRecord1, "Gui thong bao chao mung toi nguoi dung");

            return Mono.when(
                    kafkaSender.send(Mono.just(senderRecord)),
                    kafkaSender.send(Mono.just(senderRecord1))
            )
                    .onErrorResume(error -> Mono.error(
                            new RuntimeException("Gửi các event thất bại (profileService và notificationService)", error)
                    ))
                    .then(Mono.defer(() -> r2dbcEntityTemplate.insert(User.class).using(user)))
                    .map(userMapper::toUserResponse);
        });
    }

    // Cac thao tac khi nguoi dung quen mat khau

    public Mono<Void> checkAndSendCodeForForgetPassword(String email) {
        return userRepository.existsByEmail(email)
                .flatMap(exists -> {
                    if (!exists) {
                        return Mono.error(new AppException(ErrorCode.EMAIL_NOT_LINKED));
                    }

                    // Send code toi NotificationService de gui toi email cua nguoi dung
                    String code = RandomCode.generateRandomCode(10);
                    var event = ForgetPasswordEvent.builder()
                            .email(email)
                            .code(code)
                            .build();
                    ProducerRecord<String, Object> producerRecord = new ProducerRecord<>("forget_password_event", email, event);
                    SenderRecord<String, Object, String> senderRecord = SenderRecord.create(producerRecord, "Gui code de nguoi dung xac nhan email de doi mat khau");
                    return kafkaSender.send(Mono.just(senderRecord))
                            .doOnError(throwable -> log.error("Loi khi gui event den NotificationService: {}", throwable.getMessage()))
                            .then(reactiveRedisTemplate.opsForValue().set("forget_password:" + email, code, Duration.ofMinutes(2)));

                })
                .then();
    }

    public Mono<String> verifyAndSendNewPasswordToUser(VerifyForgetPasswordRequest request) {
        return reactiveRedisTemplate.opsForValue().get("forget_password:" + request.getEmail())
                .flatMap(code -> {
                    if (code == null) {
                        return Mono.error(new AppException(ErrorCode.TIMEOUT));
                    }

                    if (!code.equals(request.getCode())) {
                        return Mono.error(new AppException(ErrorCode.INVALID_VERIFICATION_CODE));
                    }

                    String newPassword = RandomCode.generateRandomPassword(12);
                    var newPasswordEvent = NewPasswordEvent.builder()
                            .email(request.getEmail())
                            .newPassword(newPassword)
                            .build();
                    ProducerRecord<String, Object> producerRecord = new ProducerRecord<>("new_password_event", request.getEmail(), newPasswordEvent);
                    SenderRecord<String, Object, String> senderRecord = SenderRecord.create(producerRecord, "Gui mat khau moi den nguoi dung de nguoi dung co the dang nhap");

                    return userRepository.findByEmail(request.getEmail())
                            .flatMap(user -> {
                                user.setPassword(passwordEncoder.encode(newPassword));
                                return kafkaSender.send(Mono.just(senderRecord))
                                        .then(userRepository.save(user))
                                        .onErrorResume(throwable -> Mono.error(new AppException(ErrorCode.SEND_PASSWORD_FAILED)))
                                        .then(Mono.just("Mat khau moi da duoc gui den hop thu email, nhap mat khau moi de dang nhap"));
                            });
                });
    }

    public Mono<String> verifyAndSetUsernameAndSendNewPasswordToUser(VerifyForgetPasswordRequest request) {
        return reactiveRedisTemplate.opsForValue().get("forget_password:" + request.getEmail())
                .flatMap(code -> {
                    if (code == null) {
                        return Mono.error(new AppException(ErrorCode.TIMEOUT));
                    }

                    if (!code.equals(request.getCode())) {
                        return Mono.error(new AppException(ErrorCode.INVALID_VERIFICATION_CODE));
                    }

                    String newPassword = RandomCode.generateRandomPassword(12);
                    var newPasswordEvent = NewPasswordEvent.builder()
                            .email(request.getEmail())
                            .newPassword(newPassword)
                            .build();
                    ProducerRecord<String, Object> producerRecord = new ProducerRecord<>("new_password_event", request.getEmail(), newPasswordEvent);
                    SenderRecord<String, Object, String> senderRecord = SenderRecord.create(producerRecord, "Gui mat khau moi den nguoi dung de nguoi dung co the dang nhap");

                    return userRepository.findByEmail(request.getEmail())
                            .flatMap(user -> {
                                user.setUsername(request.getEmail());
                                user.setPassword(passwordEncoder.encode(newPassword));
                                return kafkaSender.send(Mono.just(senderRecord))
                                        .then(userRepository.save(user))
                                        .onErrorResume(throwable -> Mono.error(new AppException(ErrorCode.SEND_PASSWORD_FAILED)))
                                        .then(Mono.just("Mat khau va ten dang nhap da duoc thay doi. Trong do, mat khau duoc gui trong email con username la email"));
                            });
                });
    }

    public Flux<UserResponse> getAllUser() {
        return userRepository.findAll()
                .map(userMapper::toUserResponse);
    }

    public Mono<UserResponse> getById(String userId) {
        return userRepository.findById(userId)
                .map(userMapper::toUserResponse);
    }

    public Mono<Void> deleteAll() {
        return userRepository.deleteAll();
    }

    public Mono<Void> deleteByUserId(String userId) {
        return userRepository.deleteById(userId);
    }
}
