package com.dauducbach.identity_service.service;


import com.dauducbach.identity_service.repository.UserRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.server.WebFilterExchange;
import org.springframework.security.web.server.authentication.ServerAuthenticationSuccessHandler;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Objects;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j

public class SocialLoginCompleteService implements ServerAuthenticationSuccessHandler {
    UserRepository userRepository;
    AuthenticationService authenticationService;

    @Override
    public Mono<Void> onAuthenticationSuccess(WebFilterExchange webFilterExchange, Authentication authentication) {
        log.info("Authentication: {}", authentication);
        var oauthToken = (OAuth2AuthenticationToken) authentication;
        var principal = (DefaultOAuth2User) oauthToken.getPrincipal();

        String provider = oauthToken.getAuthorizedClientRegistrationId();
        String providerId = extractProviderId(principal, provider);

        return userRepository.findByProviderId(providerId)
                .flatMap(authenticationService::generateToken)
                .flatMap(token -> {
                    var response = webFilterExchange.getExchange().getResponse();
                    response.getHeaders().add("Content-Type", "application/json");

                    String body = String.format("{\"token\":\"%s\"}", token);
                    var buffer = response.bufferFactory().wrap(body.getBytes());
                    log.info("Token: {}", token);

                    return response.writeWith(Mono.just(buffer));
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
}
