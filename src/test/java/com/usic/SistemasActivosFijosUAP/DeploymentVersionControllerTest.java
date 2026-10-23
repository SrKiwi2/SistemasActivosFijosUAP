package com.usic.SistemasActivosFijosUAP;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;

import com.usic.SistemasActivosFijosUAP.config.DeploymentVersion;
import com.usic.SistemasActivosFijosUAP.controller.rest.DeploymentVersionController;

class DeploymentVersionControllerTest {
    @Test
    void exponeLaVersionDelBuildSinCacheInclusoTrasReiniciarLaSesion() {
        Properties propiedades = new Properties();
        propiedades.setProperty("version", "2.0");
        propiedades.setProperty("time", "2026-10-23T14:00:00Z");
        @SuppressWarnings("unchecked")
        ObjectProvider<BuildProperties> proveedor = mock(ObjectProvider.class);
        when(proveedor.getIfAvailable()).thenReturn(new BuildProperties(propiedades));
        DeploymentVersionController controller = new DeploymentVersionController(new DeploymentVersion(proveedor));

        var respuesta = controller.version();
        assertEquals(200, respuesta.getStatusCode().value());
        assertEquals("2.0-2026-10-23T14:00:00Z", respuesta.getBody().get("version"));
        assertNotNull(respuesta.getHeaders().getCacheControl());
    }
}
