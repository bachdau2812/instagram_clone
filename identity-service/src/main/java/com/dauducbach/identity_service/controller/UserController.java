package com.dauducbach.identity_service.controller;

import com.dauducbach.identity_service.dto.request.EmailVerifyRequest;
import com.dauducbach.identity_service.dto.request.UserCreationRequest;
import com.dauducbach.identity_service.dto.request.VerifyForgetPasswordRequest;
import com.dauducbach.identity_service.dto.response.UserResponse;
import com.dauducbach.identity_service.service.UserService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)

@RequestMapping("/users")
public class UserController {
    UserService userService;

    @PostMapping("/pre-register")
    Mono<Void> preRegister(@RequestBody UserCreationRequest request) {
        return userService.preRegister(request);
    }

    @GetMapping("/send-code")
    Mono<Void> sendCode(@RequestParam String email, @RequestParam String username){
        return userService.sendCode(email, username);
    }

    @PostMapping("/email-verify")
    Mono<UserResponse> verifyAndCreateUser(@RequestBody EmailVerifyRequest request) {
        return userService.emailVerifyAndCreateUser(request);
    }

    @GetMapping("/check-email")
    Mono<Void> checkEmailForForgetPassword(@RequestParam String email) {
        return userService.checkAndSendCodeForForgetPassword(email);
    }

    @PostMapping("/forget-password-verify")
    Mono<String> verifyAndSendNewPassword(@RequestBody VerifyForgetPasswordRequest request) {
        return userService.verifyAndSendNewPasswordToUser(request);
    }

    @PostMapping("/forget-password-verify_f")
    Mono<String> verifyAndSetUserAndSendNewPassword(@RequestBody VerifyForgetPasswordRequest request) {
        return userService.verifyAndSetUsernameAndSendNewPasswordToUser(request);
    }

    @GetMapping("/get-all")
    Flux<UserResponse> getAllUser() {
        return userService.getAllUser();
    }

}
