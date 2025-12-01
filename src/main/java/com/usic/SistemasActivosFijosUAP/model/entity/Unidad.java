package com.usic.SistemasActivosFijosUAP.model.entity;

import java.util.ArrayList;
import java.util.List;

import com.usic.SistemasActivosFijosUAP.config.AuditoriaConfig;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Setter @Getter
@Table(name = "unidads")
@NoArgsConstructor
@AllArgsConstructor
public class Unidad extends AuditoriaConfig{
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long idUnidad;

    @Column(name = "nombre", nullable = false, length = 255)
    private String nombre;

    // Relación: Movimientos donde esta unidad es origen
    @OneToMany(mappedBy = "unidadOrigen", cascade = CascadeType.ALL, orphanRemoval = false, fetch = FetchType.LAZY)
    private List<Movimiento> movimientosOrigen = new ArrayList<>();

    // Relación: Movimientos donde esta unidad es destino
    @OneToMany(mappedBy = "unidadDestino", cascade = CascadeType.ALL, orphanRemoval = false, fetch = FetchType.LAZY)
    private List<Movimiento> movimientosDestino = new ArrayList<>();

    @Override
    public String toString() {
        return nombre;
    }
}
