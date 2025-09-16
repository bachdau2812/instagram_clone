package com.dauducbach.identity_service.configuration;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.SignedJWT;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.ServerAuthenticationFailureHandler;
import org.springframework.security.web.server.authentication.ServerAuthenticationSuccessHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.text.ParseException;
import java.util.List;


@Configuration
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
@RequiredArgsConstructor

public class SecurityConfig {
    @Value("${jwt.signerKey}")
    private String SIGNED_KEY;

    public final String[] PUBLIC_ENDPOINT = {
            "/login/oauth2/code/**",
            "/oauth2/authorization/**",
            "/login/**",
            "/auth/login",
            "/auth/verify-token",
            "/users/pre-register",
            "/users/send-code",
            "/users/email-verify",
            "/users/check-email",
            "/users/forget-password-verify",
            "/users/forget-password-verify_f"
    };

    @Bean
    public SecurityWebFilterChain securityFilterChain(ServerHttpSecurity serverHttpSecurity, ServerAuthenticationSuccessHandler successHandler,
                                                      ServerAuthenticationFailureHandler failureHandler) {
        serverHttpSecurity.csrf(ServerHttpSecurity.CsrfSpec::disable);

        serverHttpSecurity.authorizeExchange(
                authorizeExchangeSpec -> authorizeExchangeSpec
                        .pathMatchers(PUBLIC_ENDPOINT).permitAll()
                        .anyExchange().authenticated()
        );

        serverHttpSecurity.oauth2ResourceServer(
                oAuth2ResourceServerSpec -> oAuth2ResourceServerSpec
                        .jwt(jwtSpec -> jwtSpec
                                .jwtDecoder(reactiveJwtDecoder())
                                .jwtAuthenticationConverter(reactiveJwtAuthenticationConverter())
                        )
        );

        serverHttpSecurity.oauth2Login(oauth2 -> oauth2
                .authenticationSuccessHandler(successHandler)
                .authenticationFailureHandler(failureHandler)
        );

        return serverHttpSecurity.build();
    }

    @Bean
    ReactiveJwtAuthenticationConverter reactiveJwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter jwtGrantedAuthoritiesConverter = new JwtGrantedAuthoritiesConverter();
        jwtGrantedAuthoritiesConverter.setAuthorityPrefix("");

        ReactiveJwtAuthenticationConverter reactiveJwtAuthenticationConverter = new ReactiveJwtAuthenticationConverter();
        reactiveJwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(
                jwt -> Flux.fromIterable(jwtGrantedAuthoritiesConverter.convert(jwt))
        );

        return reactiveJwtAuthenticationConverter;
    }

    @Bean
    ReactiveJwtDecoder reactiveJwtDecoder() {
        return new ReactiveJwtDecoder() {
            @Override
            public Mono<Jwt> decode(String token) throws JwtException {
                try{
                    SignedJWT signedJWT = SignedJWT.parse(token);
                    JWSVerifier jwsVerifier = new MACVerifier(SIGNED_KEY.getBytes());

                    if (!signedJWT.verify(jwsVerifier)) {
                        return Mono.error(new RuntimeException("Invalid Signature!"));
                    }

                    Jwt jwt = new Jwt(
                            token,
                            signedJWT.getJWTClaimsSet().getIssueTime().toInstant(),
                            signedJWT.getJWTClaimsSet().getExpirationTime().toInstant(),
                            signedJWT.getHeader().toJSONObject(),
                            signedJWT.getJWTClaimsSet().getClaims()
                    );

                    return Mono.just(jwt);
                }catch (ParseException | JOSEException e) {
                    throw new RuntimeException(e);
                }
            }
        };
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of("http://localhost:49638"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true); // nếu FE gửi kèm cookie/session

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        return source;
    }
}
