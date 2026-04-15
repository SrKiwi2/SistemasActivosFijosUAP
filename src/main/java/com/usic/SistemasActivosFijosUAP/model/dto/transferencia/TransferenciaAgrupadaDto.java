package com.usic.SistemasActivosFijosUAP.model.dto.transferencia;

import java.time.LocalDate;
import java.util.List;

import com.usic.SistemasActivosFijosUAP.model.dto.interoperabilidad.TransferenciaValidadaDto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class TransferenciaAgrupadaDto {
    
    private String corrT;
    private String nombreT;
    private LocalDate fechaT;
    private String estadoT;

    // Origen (igual para todos los activos del grupo)
    private String  unidadO;
    private Short   codOficO;
    private Short   codRespO;
    private String  ciSolO;

    // Destino (igual para todos)
    private String  unidadD;
    private Short   codOficD;
    private String  ciRecep;
    private String  nomRecep;

    // Tipo determinado por unidadO vs unidadD
    private TransferenciaValidadaDto.TipoTransferencia tipo;

    // Lista de activos individuales con su validación
    private List<TransferenciaValidadaDto> activos;

    // ── Campos calculados ────────────────────────────────────────────────────

    // true solo si TODOS los activos son válidos
    public boolean isTodosValidos() {
        return activos != null &&
               !activos.isEmpty() &&
               activos.stream().allMatch(TransferenciaValidadaDto::isValida);
    }

    // Cuenta activos con problemas
    public long getActivosConError() {
        return activos == null ? 0 :
               activos.stream().filter(a -> !a.isValida()).count();
    }

    // true si ya fue aprobada en BD (basta con que uno esté aprobado)
    public boolean isYaAprobada() {
        return activos != null &&
               activos.stream().anyMatch(TransferenciaValidadaDto::isYaAprobadaEnBd);
    }
}
