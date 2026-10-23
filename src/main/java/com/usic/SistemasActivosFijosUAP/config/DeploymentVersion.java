package com.usic.SistemasActivosFijosUAP.config;

import java.util.UUID;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Component;

/** Identifica el binario que atiende la página; cambia al recompilar y desplegar. */
@Component("deploymentVersion")
public class DeploymentVersion {
    private final String version;

    public DeploymentVersion(ObjectProvider<BuildProperties> buildInfo) {
        BuildProperties build = buildInfo.getIfAvailable();
        this.version = build != null && build.getTime() != null
                ? build.getVersion() + "-" + build.getTime()
                : "arranque-" + UUID.randomUUID();
    }

    public String getVersion() {
        return version;
    }
}
