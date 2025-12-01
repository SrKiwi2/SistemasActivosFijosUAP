package com.usic.SistemasActivosFijosUAP.model.entity;

import java.math.BigDecimal;
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
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "hoja_rutas")
@Setter @Getter
@NoArgsConstructor
@AllArgsConstructor
public class HojaRuta extends AuditoriaConfig {
    
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long idHojaRuta;

    @Column(name = "codigo", nullable = false, length = 255)
    private String codigo;

    @Column(name = "tipo", columnDefinition = "TEXT")
    private String tipo; // RECTORADO, etc.

    @Column(name = "descripcion", columnDefinition = "TEXT")
    private String descripcion;

    @Column(name = "certificacion", length = 255)
    private String certificacion;

    @Column(name = "monto", precision = 15, scale = 2)
    private BigDecimal monto;

    @Column(name = "gestion")
    private Integer gestion; // Año de gestión: 2024

    // Relación: Muchos a Uno con Solicitante
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "solicitante_id", nullable = false)
    private Solicitante solicitante;

    // Relación: Uno a Muchos con Movimiento
    @OneToMany(mappedBy = "hojaRuta", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<Movimiento> movimientos = new ArrayList<>();

    // Métodos helper
    public void agregarMovimiento(Movimiento movimiento) {
        movimiento.setHojaRuta(this);
        this.movimientos.add(movimiento);
    }

    public void removerMovimiento(Movimiento movimiento) {
        this.movimientos.remove(movimiento);
        movimiento.setHojaRuta(null);
    }

    @Override
    public String toString() {
        return codigo + " - " + descripcion;
    }
}
