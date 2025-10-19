package com.dauducbach.chat_service.controller;

import com.dauducbach.chat_service.dto.request.AddUserToInboxRequest;
import com.dauducbach.chat_service.dto.request.CreateInboxRequest;
import com.dauducbach.chat_service.dto.request.GetPresenceOfListUser;
import com.dauducbach.chat_service.dto.request.SendMessageRequest;
import com.dauducbach.chat_service.dto.response.ListConversationOfUser;
import com.dauducbach.chat_service.dto.response.PresenceResponse;
import com.dauducbach.chat_service.dto.response.UserOffline;
import com.dauducbach.chat_service.entity.Inbox;
import com.dauducbach.chat_service.entity.Messages;
import com.dauducbach.chat_service.service.ChatService;
import com.dauducbach.chat_service.service.PresenceService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)

public class ChatController {
    ChatService chatService;
    PresenceService presenceService;

    @PostMapping("/create-inbox")
    public Mono<Inbox> createInbox(@RequestBody CreateInboxRequest request) {
        return chatService.createInbox(request);
    }

    @PostMapping("/add-to-inbox")
    public Mono<String> addUserToInbox(@RequestBody AddUserToInboxRequest request) {
        return chatService.addUserToInbox(request);
    }

    @GetMapping("/remove-from-inbox")
    public Mono<String> removeUserFromInbox(@RequestParam String userId, @RequestParam String inboxId) {
        return chatService.removeUserFromInbox(userId, inboxId);
    }

    @GetMapping("/get-list-conversation")
    public Mono<ListConversationOfUser> getListConversationOfUser(
            @RequestParam String userId,
            @RequestParam int currentIndex,
            @RequestParam int limit
    ) {
        return chatService.getListConversationOfUser(userId, currentIndex, limit);
    }

    @PostMapping("/get-presence")
    public Mono<PresenceResponse> getPresence(@RequestBody GetPresenceOfListUser request) {
        return presenceService.getPresenceOfListUser(request);
    }

    @PostMapping("/first-message")
    public Mono<Messages> firstMessage(@RequestBody SendMessageRequest request) {
        return chatService.handleMessage(request);
    }

    @GetMapping(value = "/receive-offline", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<UserOffline> notifyOffline(@RequestParam String userId) {
        return presenceService.offlineStream(userId);
    }
}
