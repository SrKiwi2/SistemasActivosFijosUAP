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
@Setter
@Getter
@Table(name = "solicitantes")
@NoArgsConstructor
@AllArgsConstructor
public class Solicitante extends AuditoriaConfig {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long idSolicitante;

    @Column(name = "nombre", length = 255)
    private String nombre;

    @Column(name = "cargo", length = 255)
    private String cargo;

    // Relación uno a muchos con HojaRuta
    @OneToMany(mappedBy = "solicitante", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<HojaRuta> hojaRutas = new ArrayList<>();

    // Relación uno a muchos con Movimiento
    @OneToMany(mappedBy = "solicitante", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<Movimiento> movimientos = new ArrayList<>();

    // Métodos helper
    public void agregarHojaRuta(HojaRuta hojaRuta) {
        hojaRuta.setSolicitante(this);
        this.hojaRutas.add(hojaRuta);
    }

    public void removerHojaRuta(HojaRuta hojaRuta) {
        this.hojaRutas.remove(hojaRuta);
        hojaRuta.setSolicitante(null);
    }
}
