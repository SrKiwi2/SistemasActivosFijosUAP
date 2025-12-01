package com.usic.SistemasActivosFijosUAP.model.entity;

import java.time.LocalDate;
import java.time.LocalTime;

import com.usic.SistemasActivosFijosUAP.config.AuditoriaConfig;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Setter @Getter
@Table(name = "movimientos")
@NoArgsConstructor
@AllArgsConstructor
public class Movimiento extends AuditoriaConfig {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long idMovimiento;

    // Relación: Muchos a Uno con HojaRuta
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "hoja_ruta_id", nullable = false)
    private HojaRuta hojaRuta;

    @Column(name = "fecha", columnDefinition = "DATE")
    private LocalDate fecha;

    @Column(name = "hora", columnDefinition = "TIME")
    private LocalTime hora;

    @Column(name = "estado", nullable = false, length = 255)
    private String estadoMovimiento; // PENDIENTE, EN_TRANSITO, ENTREGADO, etc.

    @Column(name = "observacion", length = 255)
    private String observacion;

    // Relación: Muchos a Uno con Solicitante
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "solicitante_id", nullable = false)
    private Solicitante solicitante;

    // Relación: Muchos a Uno con Unidad (Origen)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "unidad_origen_id", nullable = false)
    private Unidad unidadOrigen;

    // Relación: Muchos a Uno con Unidad (Destino)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "unidad_destino_id", nullable = false)
    private Unidad unidadDestino;

    // ⚠️ Método helper para obtener hora segura
    public String getHoraSegura() {
        return hora != null ? hora.toString() : "00:00:00";
    }

    // ⚠️ Método helper para obtener fecha segura
    public String getFechaSegura() {
        return fecha != null ? fecha.toString() : "";
    }

    @Override
    public String toString() {
        return "Movimiento {" +
                "id=" + idMovimiento +
                ", estado='" + estadoMovimiento + '\'' +
                ", fecha=" + fecha +
                '}';
    }
    
}