package com.example.API.Gateway.controller;

import com.example.API.Gateway.dto.AggregatedRegisterDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/api/gateway")
@RequiredArgsConstructor
@Slf4j
public class GatewayAuthController {

    private final WebClient webClient;

    @Value("${services.auth.url}")
    private String authServiceUrl;

    @Value("${services.user.url}")
    private String userServiceUrl;

    @PostMapping("/register")
    public Mono<ResponseEntity<String>> register(@Valid @RequestBody AggregatedRegisterDTO regDto) {
        log.info("Starting registration saga for email: {}", regDto.email());

        Map<String, String> authRequest = Map.of(
                "email", regDto.email(),
                "password", regDto.password()
        );

        Map<String, String> userRequest = Map.of(
                "email", regDto.email(),
                "name", regDto.name(),
                "surname", regDto.surname(),
                "birthDate", regDto.birthDate()
        );

        return webClient.post()
                .uri(authServiceUrl + "/api/auth/register")
                .bodyValue(authRequest)
                .retrieve()
                .toBodilessEntity()
                .doOnSuccess(r -> log.debug("Step 1: Auth credentials created"))
                .flatMap(authResponse -> {

                    return webClient.post()
                            .uri(userServiceUrl + "/api/users/internal/create")
                            .bodyValue(userRequest)
                            .retrieve()
                            .bodyToMono(Long.class) // Получаем реальный ID
                            .doOnNext(id -> log.debug("Step 2: User profile created with ID: {}", id))
                            .flatMap(realUserId -> {

                                return webClient.put()
                                        .uri(uriBuilder -> UriComponentsBuilder
                                                .fromUriString(authServiceUrl)
                                                .path("/api/auth/internal/sync-id")
                                                .queryParam("email", regDto.email())
                                                .queryParam("userId", realUserId)
                                                .build()
                                                .toUri())
                                        .retrieve()
                                        .toBodilessEntity()
                                        .map(r -> {
                                            log.info("Step 3: ID synchronized. Registration complete.");
                                            return ResponseEntity.ok("Registration successful");
                                        });
                            });
                })
                .onErrorResume(e -> handleRegistrationError(e, regDto.email()));
    }

    private Mono<ResponseEntity<String>> handleRegistrationError(Throwable e, String email) {
        log.error("Registration failed for {}: {}", email, e.getMessage());
        if (e instanceof WebClientResponseException ex && ex.getStatusCode().equals(HttpStatus.CONFLICT)) {
            return Mono.just(ResponseEntity.status(HttpStatus.CONFLICT).body("User already exists"));
        }

        return webClient.delete()
                .uri(authServiceUrl + "/api/auth/internal/rollback/" + email)
                .retrieve()
                .toBodilessEntity()
                .doOnSuccess(v -> log.info("Rollback successful for {}", email))
                .onErrorResume(rollbackEx -> {
                    log.error("Rollback failed! Data inconsistency for email: {}", email);
                    return Mono.empty();
                })
                .then(Mono.just(ResponseEntity.badRequest().body("Registration failed. Please try again.")));
    }
}