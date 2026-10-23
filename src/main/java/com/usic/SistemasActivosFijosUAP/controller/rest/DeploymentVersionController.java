package com.usic.SistemasActivosFijosUAP.controller.rest;

import java.util.Map;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.usic.SistemasActivosFijosUAP.config.DeploymentVersion;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/eventos")
@RequiredArgsConstructor
public class DeploymentVersionController {
    private final DeploymentVersion deploymentVersion;

    @GetMapping("/version")
    public ResponseEntity<Map<String, String>> version() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(Map.of("version", deploymentVersion.getVersion()));
    }
}
