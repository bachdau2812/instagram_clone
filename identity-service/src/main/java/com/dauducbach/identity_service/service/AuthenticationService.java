package com.dauducbach.identity_service.service;

import com.dauducbach.identity_service.dto.request.AuthenticationRequest;
import com.dauducbach.identity_service.dto.request.LogoutRequest;
import com.dauducbach.identity_service.dto.request.RefreshTokenRequest;
import com.dauducbach.identity_service.dto.request.VerifyTokenRequest;
import com.dauducbach.identity_service.dto.response.AuthenticationResponse;
import com.dauducbach.identity_service.entity.InvalidatedToken;
import com.dauducbach.identity_service.entity.User;
import com.dauducbach.identity_service.exception.AppException;
import com.dauducbach.identity_service.exception.ErrorCode;
import com.dauducbach.identity_service.repository.InvalidatedTokenRepository;
import com.dauducbach.identity_service.repository.UserRepository;
import com.dauducbach.identity_service.repository.UserRoleRepository;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.text.ParseException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.StringJoiner;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j

public class AuthenticationService {
    UserRepository userRepository;
    InvalidatedTokenRepository invalidatedTokenRepository;
    UserRoleRepository userRoleRepository;
    PasswordEncoder passwordEncoder;
    R2dbcEntityTemplate r2dbcEntityTemplate;

    @NonFinal
    @Value("${jwt.signerKey}")
    private String SIGNER_KEY;

    @NonFinal
    @Value("${jwt.valid-duration}")
    private Long VALID_DURATION;

    @NonFinal
    @Value("${jwt.refreshable-duration}")
    private Long REFRESHABLE_DURATION;

    public Mono<AuthenticationResponse> authenticate(AuthenticationRequest request) {
        log.info("Request: {}", request);
        return userRepository.findByUsername(request.getUsername())
                .switchIfEmpty(Mono.error(new AppException(ErrorCode.USER_NOT_FOUND)))
                .flatMap(user -> {
                    boolean isValid = passwordEncoder.matches(request.getPassword(), user.getPassword());

                    if (isValid) {
                        return generateToken(user)
                                .map(token -> AuthenticationResponse.builder()
                                        .token(token)
                                        .build());
                    } else {
                        return Mono.error(new AppException(ErrorCode.PASSWORD_INCORRECT));
                    }
                });
    }

    public Mono<String> generateToken(User user) {
        JWSHeader jwsHeader = new JWSHeader(JWSAlgorithm.HS512);
        return buildScope(user)
                .flatMap(scope -> {
                    JWTClaimsSet jwtClaimsSet = new JWTClaimsSet.Builder()
                            .subject(user.getId())
                            .issuer("com.dauducbach")
                            .issueTime(new Date())
                            .expirationTime(new Date(Instant.now().plus(VALID_DURATION, ChronoUnit.SECONDS).toEpochMilli()))
                            .jwtID(UUID.randomUUID().toString())
                            .claim("scope", scope)
                            .build();

                    Payload payload = new Payload(jwtClaimsSet.toJSONObject());
                    JWSObject jwsObject = new JWSObject(jwsHeader, payload);

                    try{
                        jwsObject.sign(new MACSigner(SIGNER_KEY.getBytes()));
                        return Mono.just(jwsObject.serialize());
                    } catch (JOSEException e) {
                        throw new RuntimeException(e);
                    }
                });
    }

    public Mono<String> buildScope(User user) {
        return userRoleRepository.findByUserId(user.getId())
                .collectList()
                .map(userRoles -> {
                    StringJoiner stringJoiner = new StringJoiner(" ");
                    userRoles.forEach(userRoles1 -> stringJoiner.add("ROLE_" + userRoles1.getRoleName()));
                    return stringJoiner.toString();
                });
    }

    public Mono<Boolean> verifyToken(VerifyTokenRequest request){
        return Mono.defer(() -> {
            try {
                JWSVerifier jwsVerifier = new MACVerifier(SIGNER_KEY.getBytes());
                SignedJWT signedJWT = SignedJWT.parse(request.getToken());

                boolean verify = signedJWT.verify(jwsVerifier);
                Date expiryTime = signedJWT.getJWTClaimsSet().getExpirationTime();
                return invalidatedTokenRepository.existsById(signedJWT.getJWTClaimsSet().getJWTID())
                        .flatMap(exists -> {
                            if (!verify || expiryTime.before(new Date()) || exists) {
                                return Mono.error(new AppException(ErrorCode.INVALID_TOKEN));
                            }
                            return Mono.just(true);
                        });
            } catch (JOSEException | ParseException e) {
                return Mono.error(new AppException(ErrorCode.TOKEN_VERIFICATION_FAILED));
            }
        });
    }

    public Mono<AuthenticationResponse> refreshToken(RefreshTokenRequest request) {
        return Mono.defer(() -> {
            try {
                SignedJWT oldToken = SignedJWT.parse(request.getToken());
                JWTClaimsSet oldClaims = oldToken.getJWTClaimsSet();

                Instant now = Instant.now();
                Date newIssueTime = Date.from(now);
                Date newExpiryTime = Date.from(now.plus(REFRESHABLE_DURATION, ChronoUnit.SECONDS));

                JWSHeader jwsHeader = new JWSHeader(JWSAlgorithm.HS512);
                JWTClaimsSet newClaimsSet = new JWTClaimsSet.Builder()
                        .subject(oldClaims.getSubject())
                        .issuer(oldClaims.getIssuer())
                        .issueTime(newIssueTime)
                        .expirationTime(newExpiryTime)
                        .jwtID(oldClaims.getJWTID())
                        .claim("scope", oldClaims.getClaim("scope"))
                        .build();

                Payload payload = new Payload(newClaimsSet.toJSONObject());
                JWSObject jwsObject = new JWSObject(jwsHeader, payload);

                jwsObject.sign(new MACSigner(SIGNER_KEY));
                return Mono.just(AuthenticationResponse.builder().token(jwsObject.serialize()).build());
            } catch (ParseException | JOSEException e) {
                return Mono.error(new AppException(ErrorCode.REFRESH_TOKEN_FAILED));
            }
        });
    }

    public Mono<Void> logout(LogoutRequest request) {
        return Mono.defer(() -> {
            try {
                SignedJWT signedJWT = SignedJWT.parse(request.getToken());

                String tokenId = signedJWT.getJWTClaimsSet().getJWTID();
                Instant expiryTime = signedJWT.getJWTClaimsSet().getExpirationTime().toInstant();

                var invalidatedToken = InvalidatedToken.builder()
                        .id(tokenId)
                        .expiryTime(expiryTime)
                        .build();

                return r2dbcEntityTemplate.insert(InvalidatedToken.class).using(invalidatedToken)
                        .then();
            } catch (ParseException e) {
                throw new RuntimeException(e);
            }
        });
    }
}
