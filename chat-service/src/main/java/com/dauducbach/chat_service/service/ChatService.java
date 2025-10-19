package com.dauducbach.chat_service.service;

import com.dauducbach.chat_service.constant.GroupRole;
import com.dauducbach.chat_service.constant.InboxType;
import com.dauducbach.chat_service.dto.request.*;
import com.dauducbach.chat_service.dto.response.*;
import com.dauducbach.chat_service.entity.Inbox;
import com.dauducbach.chat_service.entity.Messages;
import com.dauducbach.chat_service.entity.UserInbox;
import com.dauducbach.chat_service.repository.InboxRepository;
import com.dauducbach.chat_service.repository.MessageRepository;
import com.dauducbach.chat_service.repository.UserInboxRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RequiredArgsConstructor
@Slf4j

public class ChatService {
    InboxRepository inboxRepository;
    MessageRepository messageRepository;
    UserInboxRepository userInboxRepository;
    R2dbcEntityTemplate r2dbcEntityTemplate;
    private final WebClient webClient;

    public Mono<Messages> handleMessage(SendMessageRequest request) {
        // Kiem tra inbox co ton tai hay khong
        return inboxRepository.findById(request.getInboxId())
                .switchIfEmpty(createInbox(CreateInboxRequest.builder()
                        .type(InboxType.PRIVATE)
                        .recipientId(request.getRecipientId())
                        .name("One to one chat")
                        .build()))
                .flatMap(inbox -> {
                    var message = Messages.builder()
                            .id(request.getMessageId())
                            .inboxUid(inbox.getId())
                            .senderId(request.getSenderId())
                            .content(request.getContent())
                            .createdAt(Instant.now())
                            .updateAt(Instant.now())
                            .build();

                    return r2dbcEntityTemplate.insert(Messages.class).using(message);
                });
    }

    public Mono<Inbox> createInbox(CreateInboxRequest request) {
        log.info("1. Create inbox: {}", request);
        return ReactiveSecurityContextHolder.getContext()
                .map(securityContext -> ((Jwt) securityContext.getAuthentication().getPrincipal()).getSubject())
                .flatMap(requesterId -> {
                    var inbox = Inbox.builder()
                            .id(UUID.randomUUID().toString())
                            .type(request.getType())
                            .name(request.getName())
                            .lastMessageId("")
                            .lastSentUserId("")
                            .createdAt(Instant.now())
                            .build();

                    log.info("2. Inbox: {}", inbox);

                    if (request.getType().equals(InboxType.PRIVATE)) {
                        return r2dbcEntityTemplate.insert(Inbox.class).using(inbox)
                                .then(Mono.zip(
                                        addUserToInbox(AddUserToInboxRequest.builder()
                                                .userId(requesterId)
                                                .inboxId(inbox.getId())
                                                .inboxType(InboxType.PRIVATE)
                                                .build()
                                        ),
                                        addUserToInbox(AddUserToInboxRequest.builder()
                                                .userId(request.getRecipientId())
                                                .inboxId(inbox.getId())
                                                .inboxType(InboxType.PRIVATE)
                                                .build()
                                        )
                                ))
                                .doOnSuccess(objects -> log.info("3. Add user to inbox complete: {}", objects))
                                .then(Mono.just(inbox));
                    }

                    return r2dbcEntityTemplate.insert(Inbox.class).using(inbox)
                            .then(addUserToInbox(AddUserToInboxRequest.builder()
                                    .inboxType(request.getType())
                                    .inboxId(inbox.getId())
                                    .role(GroupRole.ADMIN)
                                    .userId(requesterId)
                                    .build()
                            ))
                            .then(Mono.just(inbox));

                });
    }

    public Mono<String> addUserToInbox(AddUserToInboxRequest request) {
        return inboxRepository.findById(request.getInboxId())
                .switchIfEmpty(Mono.error(new RuntimeException("Inbox not exists")))
                .flatMap(inbox -> {
                    if (inbox.getType().equals(InboxType.PRIVATE)) {
                        var userInbox = UserInbox.builder()
                                .id(UUID.randomUUID().toString())
                                .userId(request.getUserId())
                                .inboxId(request.getInboxId())
                                .lastReadMessageId("")
                                .build();

                        return r2dbcEntityTemplate.insert(UserInbox.class).using(userInbox)
                                .then(Mono.just("Add user to inbox complete"))
                                .doOnError(throwable -> log.info("Error add user: {}", throwable.getMessage()))
                                .onErrorReturn("Add user fail !!");

                    }

                    return userInboxRepository.existsByUserIdAndInboxId(request.getUserId(), request.getInboxId())
                            .flatMap(userExists -> {
                                if (userExists) {
                                    return Mono.just("User already in inbox");
                                }

                                var userInbox = UserInbox.builder()
                                        .userId(request.getUserId())
                                        .inboxId(request.getInboxId())
                                        .lastReadMessageId("")
                                        .role(request.getRole())
                                        .joinedAt(Instant.now())
                                        .build();

                                return r2dbcEntityTemplate.insert(UserInbox.class).using(userInbox)
                                        .then(Mono.just("Add user to inbox complete"))
                                        .onErrorReturn("Add user fail !!");
                            });
                });
    }

    public Mono<String> removeUserFromInbox(String userId, String inboxId) {
        return ReactiveSecurityContextHolder.getContext()
                // Lay thong tin nguoi gui request
                .map(securityContext -> ((Jwt) securityContext.getAuthentication().getPrincipal()).getSubject())
                // Check group co ton tai hay khong
                .flatMap(requesterId -> inboxRepository.findById(inboxId)
                        .switchIfEmpty(Mono.error(new RuntimeException("Group not exists")))
                        .flatMap(inbox -> userInboxRepository.findByUserIdAndInboxId(requesterId, inboxId)
                                // Kiem tra quyen cua nguoi gui request
                                .flatMap(userInbox -> {
                                    if (userInbox.getRole().equals(GroupRole.MEMBER)) {
                                        return Mono.error(new RuntimeException("You do not have permission to remove user"));
                                    }

                                    return userInboxRepository.deleteByUserIdAndInboxId(userId, inboxId)
                                            .then(Mono.just("Delete complete"))
                                            .onErrorReturn("Delete fail !!");
                                })
                        )
                );
    }

    public Mono<ConversationResponse> getConversationById(String conversationId, int currentIndex, int limit) {
        return inboxRepository.findById(conversationId)
                .switchIfEmpty(Mono.error(new RuntimeException("Conversation not exists")))
                .flatMap(inbox -> getConversation(inbox, currentIndex, limit));
    }

    public Mono<List<UserInfo>> getUserInfo(List<String> userInfoRequest) {
        return webClient.post()
                .uri("http://localhost:8081/profile/get-basic-info")
                .bodyValue(UserBasicInfoRequest.builder()
                        .userId(userInfoRequest)
                        .build()
                )
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<UserInfo>>() {});
    }

    public Mono<Map<String, String>> getListAvtOfListUser(List<String> userInfoRequest) {
        return webClient.post()
                .uri("http://localhost:8084/storage/get/list-avt")
                .bodyValue(GetAvatarRequest.builder()
                        .userIds(userInfoRequest)
                        .build()
                )
                .retrieve()
                .bodyToMono(GetAvatarResponse.class)
                .map(GetAvatarResponse::getUserAvatarUrls);
    }


    public Mono<ConversationResponse> getConversation(Inbox inbox, int currentIndex, int limit) {
        // Lấy tất cả tin nhắn trong đoạn chat của Group
        Mono<List<Messages>> listMessage = messageRepository.findAllByInboxUid(inbox.getId())
                .sort((o1, o2) -> o2.getCreatedAt().compareTo(o1.getCreatedAt()))
                .skip(currentIndex)
                .take(limit)
                .collectList();

        return userInboxRepository.findAllByInboxId(inbox.getId())
                .collectList()
                .flatMap(userInboxes -> {
                    List<String> userInfoRequest = userInboxes.stream().map(UserInbox::getUserId).toList();
                    // Lấy danh sách thông tin của user trong group
                    Mono<List<UserInfo>> userInfo = getUserInfo(userInfoRequest);

                    // Lấy danh sách avt của mỗi thành viên trong group
                    Mono<Map<String, String>> userAvt = getListAvtOfListUser(userInfoRequest);

                    return Mono.zip(userInfo, userAvt, listMessage)
                            .flatMap(tuple ->  {
                                // Lấy kết quả danh sách user
                                List<UserInfo> listUserInfo = tuple.getT1();

                                // Lấy kết quả danh sách avt của user. Sau đó lưu vào Map để tiện cho việc lấy thông tin
                                Map<String, String> mapAvtOfUser = tuple.getT2();

                                // Lấy kết quả danh sách tin nhắn của đoạn chat
                                List<Messages> messagesList = tuple.getT3();

                                // Map dùng để lưu userInbox thuận tiện cho việc lấy tạo thông tin response của UserInfoInConversation
                                Map<String, UserInbox> userInboxMap = new HashMap<>();

                                userInboxes.forEach(userInbox -> {
                                    userInboxMap.put(userInbox.getUserId(), userInbox);
                                });

                                var listUserInfoConversation = listUserInfo.stream()
                                        .map(curUserInfo -> {
                                            var curUserInbox = userInboxMap.get(curUserInfo.getUserId());
                                            var curUserAvt = mapAvtOfUser.get(curUserInfo.getUserId());

                                            return UserInfoInConversation.builder()
                                                    .userName(curUserInfo.getDisplayName())
                                                    .userId(curUserInfo.getUserId())
                                                    .avtUrl(curUserAvt)
                                                    .role(curUserInbox.getRole())
                                                    .lastReadMessageId(curUserInbox.getLastReadMessageId())
                                                    .unreadCount(curUserInbox.getUnreadCount())
                                                    .build();
                                        })
                                        .toList();

                                var listMessageResponse = messagesList.stream()
                                        .map(curMessage -> {
                                            var curUserAvt = mapAvtOfUser.get(curMessage.getSenderId());

                                            // Map dùng để lưu thông tin của userInfo, nhằm giúp việc tạo thông tin của MessageResponse thuận tiện hơn
                                            Map<String, UserInfo> userInfoMap = new HashMap<>();
                                            listUserInfo.forEach(userInfo1 -> {
                                                userInfoMap.put(userInfo1.getUserId(), userInfo1);
                                            });

                                            return MessageResponse.builder()
                                                    .messageId(curMessage.getId())
                                                    .senderId(curMessage.getSenderId())
                                                    .senderAvtUrl(curUserAvt)
                                                    .senderDisplayName(userInfoMap.get(curMessage.getSenderId()).getDisplayName())
                                                    .createAt(curMessage.getCreatedAt())
                                                    .content(curMessage.getContent())
                                                    .build();
                                        })
                                        .toList();

                                if (inbox.getType().equals(InboxType.PRIVATE)) {
                                    return Mono.just(ConversationResponse.builder()
                                            .inboxId(inbox.getId())
                                            .messageResponses(listMessageResponse)
                                            .userInfoInConversations(listUserInfoConversation)
                                            .createAt(inbox.getCreatedAt())
                                            .build());
                                }
                                return Mono.just(ConversationResponse.builder()
                                        .inboxId(inbox.getId())
                                        .messageResponses(listMessageResponse)
                                        .userInfoInConversations(listUserInfoConversation)
                                        .inboxName(inbox.getName())
                                        .createAt(inbox.getCreatedAt())
                                        .build());
                            });
                });
    }

    public Mono<ListConversationOfUser> getListConversationOfUser(String userId, int currentIndex, int limit) {
        return ReactiveSecurityContextHolder.getContext()
                .map(securityContext -> ((Jwt) securityContext.getAuthentication().getPrincipal()).getSubject())
                .flatMap(requesterId -> {
                    if (!requesterId.equals(userId)) {
                        return Mono.error(new RuntimeException("You don't have permission"));
                    }

                    return userInboxRepository.findAllByUserId(userId)
                            .map(UserInbox::getInboxId)
                            .flatMap(inboxId -> getConversationById(inboxId, currentIndex, limit))
                            .collectList();
                })
                .map(conversationResponses -> ListConversationOfUser.builder()
                        .listConversation(conversationResponses)
                        .build()
                );
    }
}
