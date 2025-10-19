package com.dauducbach.chat_service.service;

import com.dauducbach.chat_service.constant.InboxType;
import com.dauducbach.chat_service.dto.request.*;
import com.dauducbach.chat_service.dto.response.DeliveredResponse;
import com.dauducbach.chat_service.dto.response.MessageWebSocketResponse;
import com.dauducbach.chat_service.dto.response.SentResponse;
import com.dauducbach.chat_service.repository.InboxRepository;
import com.dauducbach.chat_service.repository.UserInboxRepository;
import com.dauducbach.event.NotificationForSendMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Mono;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderRecord;

import java.net.URI;
import java.time.Instant;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j

public class ChatHandler implements WebSocketHandler {
    EncodingUtils encodingUtils;
    KafkaSender<String, Object> kafkaSender;
    ChatService chatService;
    WebSocketSessionRegistry registry;
    PresenceService presenceService;
    UserInboxRepository userInboxRepository;
    ObjectMapper objectMapper;
    InboxRepository inboxRepository;

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        String userId = getUserIdFromSession(session);
        registry.addSession(userId, session);
        log.info("Current registry: {}", registry.print());

        return presenceService.setOnline(userId)
                .then(session.receive()
                        .map(WebSocketMessage::getPayloadAsText)
                        .doOnNext(s -> log.info("1. Payload: {}", s))
                        .flatMap(payload -> {
                            JsonNode node = null;
                            try {
                                node = objectMapper.readTree(payload);
                            } catch (JsonProcessingException e) {
                                throw new RuntimeException(e);
                            }
                            String type = node.get("type").asText();

                            switch (type) {
                                case "MESSAGE" -> {
                                    SendMessageEvent sEvent = encodingUtils.decode(payload, SendMessageEvent.class);
                                    var request = SendMessageRequest.builder()
                                            .senderId(sEvent.getSenderId())
                                            .recipientId(sEvent.getRecipientId())
                                            .inboxId(sEvent.getInboxId())
                                            .content(sEvent.getContent())
                                            .messageId(sEvent.getMessageId())
                                            .build();

                                    // Phản hồi trạng thái đã gửi
                                    log.info("2. Request: {}", sEvent);
                                    var sent = SentResponse.builder()
                                            .messageId(sEvent.getMessageId())
                                            .inboxId(sEvent.getInboxId())
                                            .status("sent")
                                            .build();
                                    var sentResText = encodingUtils.encode(sent);

                                    ProducerRecord<String, Object> producerRecord = new ProducerRecord<>("handle_message_event",userId, request);
                                    SenderRecord<String, Object, String> senderRecord = SenderRecord.create(producerRecord, userId);

                                    return session.send(Mono.just(session.textMessage(sentResText)))
                                            .then(Mono.defer(() -> kafkaSender.send(Mono.just(senderRecord))
                                                    .then())
                                            )
                                            .then();
                                }

                                case "READ" -> {
                                    SeenEvent request = encodingUtils.decode(payload, SeenEvent.class);
                                    log.info("2. Read event: {}", request);

                                    ProducerRecord<String, Object> producerRecord = new ProducerRecord<>("handle_read_event",userId, request);
                                    SenderRecord<String, Object, String> senderRecord = SenderRecord.create(producerRecord, userId);

                                    return kafkaSender.send(Mono.just(senderRecord))
                                            .then();
                                }

                                default -> {
                                    PingEvent request = encodingUtils.decode(payload, PingEvent.class);
                                    log.warn("Ping event: {}", request);
                                    return Mono.empty();
                                }
                            }
                        })
                        .doOnError(throwable -> log.info("Error receive message: {}", throwable.getMessage()))
                        .doFinally(signalType -> {
                            log.info("WebSocket disconnected:{}", signalType);
                            registry.removeSession(userId);
                            log.info("Current registry: {}", registry.print());
                        })
                        .then(presenceService.setOffline(userId)));
    }

    @KafkaListener(topics = "handle_message_event")
    public void handleReadMessage(@Payload SendMessageRequest request) {
        sendMessage(request)
                .then(updateLastMessage(request.getMessageId(), request.getSenderId(), request.getInboxId()))
                .subscribe();
    }

    @KafkaListener(topics = "handle_read_event")
    public void handleReadMessage(@Payload SeenEvent event) {
        inboxRepository.findById(event.getInboxId())
                .flatMap(inbox -> {
                    if (inbox.getType().equals(InboxType.PRIVATE)) {
                        WebSocketSession sender = registry.getSession(event.getSenderId());
                        if (sender != null) {
                            return sender.send(Mono.just(sender.textMessage(encodingUtils.encode(event))))
                                    .then(updateLastSeen(event.getMessageId(), event.getViewerId(), event.getInboxId()));
                        }

                        return updateLastSeen(event.getMessageId(), event.getViewerId(), event.getInboxId());
                    } else {
                        return userInboxRepository.findAllByInboxId(event.getInboxId())
                                .filter(userInbox -> !userInbox.getUserId().equals(event.getViewerId()))
                                .flatMap(userInbox -> {
                                    WebSocketSession member = registry.getSession(userInbox.getUserId());

                                    if (member != null) {
                                        return member.send(Mono.just(member.textMessage(encodingUtils.encode(event))))
                                                .then(updateLastSeen(event.getMessageId(), event.getViewerId(), event.getInboxId()));
                                    }

                                    return updateLastSeen(event.getMessageId(), event.getViewerId(), event.getInboxId());
                                })
                                .then();
                    }
                })
                .subscribe();


    }

    public Mono<Void> updateLastSeen(String messageId, String viewerId, String inboxId) {
        return userInboxRepository.findByUserIdAndInboxId(viewerId, inboxId)
                .flatMap(userInbox -> {
                    userInbox.setLastReadMessageId(messageId);

                    return userInboxRepository.save(userInbox);
                })
                .then();
    }

    public Mono<Void> updateLastMessage(String messageId, String senderId, String inboxId) {
        return inboxRepository.findById(inboxId)
                .flatMap(inbox -> {
                    inbox.setLastMessageId(messageId);
                    inbox.setLastSentUserId(senderId);

                    return inboxRepository.save(inbox);
                })
                .then();
    }

    public Mono<Void> sendMessage(SendMessageRequest request) {
        log.info("1. Start send");
        return chatService.handleMessage(request)
                .doOnSuccess(messages -> log.info("2. Save message complete: {}", messages))
                .doOnError(throwable -> log.info("Error while save message: {}", throwable.getMessage()))
                .flatMap(messages -> {
                    var messageResponse = MessageWebSocketResponse.builder()
                            .messageId(messages.getId())
                            .senderId(request.getSenderId())
                            .inboxId(request.getInboxId())
                            .content(request.getContent())
                            .status("sent")
                            .build();

                    var textMessage = encodingUtils.encode(messageResponse);
                    log.info("Request: {}", request);

                    if (request.getRecipientId() != null) {
                        return sendMessageHelper(textMessage, request.getSenderId(), request.getRecipientId(), request.getContent(), "private", messages.getId(), messages.getInboxUid());
                    } else {
                        return userInboxRepository.findAllByInboxId(request.getInboxId())
                                .flatMap(userInbox -> sendMessageHelper(textMessage, request.getSenderId(), userInbox.getUserId(), request.getContent(), "group", messages.getId(), messages.getInboxUid()))
                                .then();
                    }
                });
    }

    public Mono<Void> sendMessageHelper(String textMessage, String senderId, String recipientId, String content, String type, String messageId, String inboxId) {
        WebSocketSession receiver = registry.getSession(recipientId);
        WebSocketSession sender = registry.getSession(senderId);
        if (receiver != null) {
            return receiver.send(Mono.just(receiver.textMessage(textMessage)))
                    .doOnSuccess(unused -> log.info("4. Send complete"))

                    .then(Mono.defer(() -> {
                        // Trien khai tranh thai da nhan
                        if (sender != null) {
                            var delivered = DeliveredResponse.builder()
                                    .messageId(messageId)
                                    .inboxId(inboxId)
                                    .status("delivered")
                                .build();

                            return sender.send(Mono.just(sender.textMessage(encodingUtils.encode(delivered))));
                        }

                        return Mono.empty();
                    }))
                    .doOnError(throwable -> log.info("Send fail: {}",throwable.getMessage()))
                    .then(Mono.defer(() -> sendNotification(senderId, recipientId, content, type)));
        } else {
            return sendNotification(senderId, recipientId, content, type);
        }
    }

    public Mono<Void> sendNotification(String senderId, String recipientId, String content, String type) {
        var event = NotificationForSendMessage.builder()
                .senderId(senderId)
                .recipientId(recipientId)
                .content(content)
                .timestamp(Instant.now())
                .type(type)
                .build();

        ProducerRecord<String, Object> producerRecord = new ProducerRecord<>("send_notification_for_chat_event",recipientId, event);
        SenderRecord<String, Object, String> senderRecord = SenderRecord.create(producerRecord, recipientId);

        return kafkaSender.send(Mono.just(senderRecord))
                .then();
    }

    public String getUserIdFromSession(WebSocketSession webSocketSession) {
        URI uri = webSocketSession.getHandshakeInfo().getUri();

        String query = uri.getQuery();
        log.info("Query: {}", query);
        if (query != null && query.startsWith("userId=")) {
            return query.substring(7);
        }
        return "unknown";
    }
}
