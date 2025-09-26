package com.usic.SistemasActivosFijosUAP.config;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/uap")
@RequiredArgsConstructor
public class UapProxyController {
    private final WebClient web = WebClient.builder().build();

  @PostMapping("/obtenerDatos")
  public Mono<ResponseEntity<String>> obtenerDatos(@RequestBody Map<String,Object> body) {
    return web.post()
      .uri("http://virtual.uap.edu.bo:7174/api/londraPost/v1/obtenerDatos")
      .header("Content-Type", "application/json")
      .header("key", "e73b1991c59a67fe182524e4d12da556136ced8a9da310c3af4c4efbde804a10") // NO en el frontend
      .bodyValue(body)
      .exchangeToMono(resp -> resp.toEntity(String.class));
  }
}
