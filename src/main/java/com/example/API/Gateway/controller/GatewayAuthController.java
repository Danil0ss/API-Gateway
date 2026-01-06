package com.example.API.Gateway.controller;

import com.example.API.Gateway.dto.AggregatedRegisterDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/api/gateway")
@RequiredArgsConstructor
public class GatewayAuthController {

    private final WebClient.Builder webClientBuilder;

    @Value("${services.auth.url}")
    private String authServiceUrl;

    @Value("${services.user.url}")
    private String userServiceUrl;

    @PostMapping("/register")
    public Mono<ResponseEntity<String>> register(@RequestBody AggregatedRegisterDTO regDto) {

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

        return webClientBuilder.build()
                .post()
                .uri(authServiceUrl + "/api/auth/register")
                .bodyValue(authRequest)
                .retrieve()
                .toBodilessEntity()
                .flatMap(authResponse -> {

                    return webClientBuilder.build()
                            .post()
                            .uri(userServiceUrl + "/api/users/internal/create")
                            .bodyValue(userRequest)
                            .retrieve()
                            .bodyToMono(Long.class)
                            .flatMap(realUserId -> {

                                return webClientBuilder.build()
                                        .put()
                                        .uri(uriBuilder -> UriComponentsBuilder
                                                .fromUriString(authServiceUrl)
                                                .path("/api/auth/internal/sync-id")
                                                .queryParam("email", regDto.email())
                                                .queryParam("userId", realUserId)
                                                .build()
                                                .toUri())
                                        .retrieve()
                                        .toBodilessEntity()
                                        .map(r -> ResponseEntity.ok("Registration successful"));
                            });
                })
                .onErrorResume(e -> {
                    System.err.println("Error: " + e.getMessage());
                    return webClientBuilder.build()
                            .delete()
                            .uri(authServiceUrl + "/api/auth/internal/rollback/" + regDto.email())
                            .retrieve()
                            .toBodilessEntity()
                            .onErrorResume(ex -> Mono.empty())
                            .then(Mono.just(ResponseEntity.badRequest().body("Registration failed. Rolled back.")));
                });
    }
}