package com.usic.SistemasActivosFijosUAP.controller.rest;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.usic.SistemasActivosFijosUAP.model.IService.IActivoService;
import com.usic.SistemasActivosFijosUAP.model.dto.ActivoFichaDTO;
import com.usic.SistemasActivosFijosUAP.model.entity.Activo;
import com.usic.SistemasActivosFijosUAP.model.entity.ConsultaApiActivo;
import com.usic.SistemasActivosFijosUAP.model.service.seguridad.ConsultaApiActivoRegistroService;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

/**
 * API pública de consulta de activos por código.
 *
 * Endpoint principal:
 *   GET /api/activos/{codigo}/ficha
 *
 * Dado el código de un activo (ej. "01-01-08-00583") devuelve, en JSON,
 * el responsable, la oficina, la unidad (predio) y la descripción del activo.
 *
 * Solo responde por activos de los grupos contables habilitados
 * ({@code api.activos.ficha.grupos-contables}, por defecto 15 = EQUIPOS DE COMPUTACION):
 * el código de una mesa o de un vehículo da 422 y no devuelve sus datos.
 *
 * Las rutas {@code /api/**} están en {@code permitAll()} en SeguridadConfig,
 * por lo que este endpoint queda accesible para sistemas externos. Cada llamada
 * queda registrada en {@code consulta_api_activo}; el sistema cliente puede
 * identificarse con la cabecera {@code X-Sistema}.
 *
 * Sin {@code @Transactional} a propósito: {@code fetchFullByCodigo} ya trae en un
 * solo viaje todas las relaciones que se leen aquí, y si la consulta fallara (p. ej.
 * un código repetido) una transacción envolvente quedaría marcada para rollback y
 * taparía la respuesta de error con otra excepción.
 */
@Slf4j
@RestController
@RequestMapping("/api/activos")
public class ActivoConsultaApiController {

    private final IActivoService activoService;
    private final ConsultaApiActivoRegistroService registro;
    private final List<Integer> gruposPermitidos;

    public ActivoConsultaApiController(IActivoService activoService,
            ConsultaApiActivoRegistroService registro,
            @Value("${api.activos.ficha.grupos-contables:15}") List<Integer> gruposPermitidos) {
        this.activoService = activoService;
        this.registro = registro;
        this.gruposPermitidos = gruposPermitidos;
    }

    @GetMapping("/{codigo}/ficha")
    public ResponseEntity<?> obtenerFichaPorCodigo(@PathVariable String codigo, HttpServletRequest request) {
        String cod = codigo == null ? null : codigo.trim();

        Optional<Activo> encontrado;
        try {
            encontrado = activoService.fetchFullByCodigo(cod);
        } catch (Exception e) {
            log.error("[API-FICHA] Error consultando codigo='{}': {}", cod, e.getMessage());
            registro.registrar(request, cod, ConsultaApiActivo.ERROR);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("mensaje", "No se pudo consultar el activo."));
        }

        if (encontrado.isEmpty()) {
            log.info("[API-FICHA] Activo no encontrado para codigo='{}'", cod);
            registro.registrar(request, cod, ConsultaApiActivo.NO_ENCONTRADO);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("mensaje", "No existe un activo con ese código."));
        }

        Activo a = encontrado.get();
        if (!esDeGrupoPermitido(a)) {
            log.info("[API-FICHA] Codigo='{}' no es de un grupo contable habilitado", cod);
            registro.registrar(request, cod, ConsultaApiActivo.GRUPO_NO_PERMITIDO);
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(Map.of("mensaje", "El código no corresponde a un equipo de computación."));
        }

        String oficina = a.getOficina() != null
                ? a.getOficina().getNombre()
                : null;

        String unidad = (a.getOficina() != null && a.getOficina().getPredio() != null)
                ? a.getOficina().getPredio().getUnidad()
                : null;

        String responsable = (a.getResponsable() != null && a.getResponsable().getPersona() != null)
                ? a.getResponsable().getPersona().getNombreCompleto()
                : null;

        registro.registrar(request, cod, ConsultaApiActivo.ENCONTRADO);
        return ResponseEntity.ok(new ActivoFichaDTO(
                a.getCodigo(),
                responsable,
                oficina,
                unidad,
                a.getDescripcion()
        ));
    }

    /** Se mira el grupo contable del activo, no el texto del código. */
    private boolean esDeGrupoPermitido(Activo a) {
        return a.getGrupoContable() != null
                && a.getGrupoContable().getCodContable() != null
                && gruposPermitidos.contains(a.getGrupoContable().getCodContable());
    }
}
