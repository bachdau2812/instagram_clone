package com.dauducbach.identity_service.controller;

import com.dauducbach.identity_service.dto.request.AuthenticationRequest;
import com.dauducbach.identity_service.dto.request.LogoutRequest;
import com.dauducbach.identity_service.dto.request.RefreshTokenRequest;
import com.dauducbach.identity_service.dto.request.VerifyTokenRequest;
import com.dauducbach.identity_service.dto.response.AuthenticationResponse;
import com.dauducbach.identity_service.service.AuthenticationService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)

@RequestMapping("/auth")
public class AuthenticationController {
    AuthenticationService authenticationService;

    @PostMapping("/login")
    Mono<AuthenticationResponse> authenticate(@RequestBody AuthenticationRequest request) {
        return authenticationService.authenticate(request);
    }

    @PostMapping("/verify-token")
    Mono<Boolean> verifyToken(@RequestBody VerifyTokenRequest request) {
        return authenticationService.verifyToken(request);
    }

    @PostMapping("/refresh-token")
    Mono<AuthenticationResponse> refreshToken(@RequestBody RefreshTokenRequest request) {
        return authenticationService.refreshToken(request);
    }

    @PostMapping("/logout")
    Mono<Void> logout(@RequestBody LogoutRequest logoutRequest) {
        return authenticationService.logout(logoutRequest);
    }
}
