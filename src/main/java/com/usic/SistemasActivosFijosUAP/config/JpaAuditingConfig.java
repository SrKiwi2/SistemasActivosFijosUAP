package com.usic.SistemasActivosFijosUAP.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Activa {@code @CreatedDate}/{@code @CreatedBy}/{@code @LastModifiedDate}/
 * {@code @LastModifiedBy} de {@code AuditoriaConfig} para las ~40 entidades que
 * extienden esa clase. Antes de esto esas anotaciones eran metadatos inertes: no
 * había {@code @EnableJpaAuditing} en ningún lado, así que Spring nunca las procesaba
 * y quien registraba/modificaba una entidad se escribía a mano, entidad por entidad
 * (ver por ejemplo {@code AsignacionEdicionService}).
 * <p>
 * Verificado antes de activar esto que nada en la interoperabilidad con el VSIAF
 * —{@code interoperabilidad/registroDbf/*DbfWriterService}, la detección de cambios
 * por hash en {@code ActivoSyncService}/{@code AuxiliarController}/
 * {@code OficinaController}/{@code ResponsableController}, ni las plantillas—
 * lee {@code registro}, {@code modificacion} o los ids de usuario para decidir nada:
 * son campos de auditoría de solo escritura, mostrados en un puñado de pantallas de
 * seguimiento. Ver {@link UsuarioAuditorAware} para de dónde sale "el usuario actual".
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "usuarioAuditorAware")
public class JpaAuditingConfig {
}
