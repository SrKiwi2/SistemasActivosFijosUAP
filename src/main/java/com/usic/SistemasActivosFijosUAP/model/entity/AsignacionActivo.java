package com.usic.SistemasActivosFijosUAP.model.entity;

import java.time.LocalDateTime;
import java.util.List;

import com.usic.SistemasActivosFijosUAP.config.AuditoriaConfig;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "asignacion")
@Setter @Getter
public class AsignacionActivo extends AuditoriaConfig{

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idAsignacion;

    private String codigoDocumento; // "1234" (El número que tú ingresas)
    private String codigoCompleto;  // "PREV. 1234" (Guardado para la posteridad)
    
    private LocalDateTime fechaAsignacion; // Fecha y hora del reporte

    @ManyToOne
    @JoinColumn(name = "id_responsable")
    private Responsable responsable; // A quién se le entrega (Lic. Ruth)

    // Relación con los activos involucrados
    @OneToMany(mappedBy = "asignacion", cascade = CascadeType.ALL)
    private List<DetalleAsignacionActivo> detalles;
}