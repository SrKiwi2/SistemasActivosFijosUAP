package com.usic.SistemasActivosFijosUAP.model.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;

import com.usic.SistemasActivosFijosUAP.model.IService.IActivoService;
import com.usic.SistemasActivosFijosUAP.model.IService.IOficinaService;
import com.usic.SistemasActivosFijosUAP.model.IService.ITransferenciaService;
import com.usic.SistemasActivosFijosUAP.model.entity.Activo;
import com.usic.SistemasActivosFijosUAP.model.entity.Oficina;
import com.usic.SistemasActivosFijosUAP.model.entity.Responsable;
import com.usic.SistemasActivosFijosUAP.model.entity.Transferencia;
import com.usic.SistemasActivosFijosUAP.model.entity.TransferenciaDetalle;
import com.usic.SistemasActivosFijosUAP.model.entity.Usuario;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TransferenciaService {

    private final ITransferenciaService transferenciaService;
    private final IActivoService activoService;
    private final IOficinaService oficinaService;

    @Transactional
    public Transferencia crearYGuardar(Responsable respOrigen,
            List<String> ubicacionesOrigen,
            LocalDate fTransf,
            Responsable respDestino,
            List<String> ubicacionesActuales,
            LocalDate fRecep,
            List<String> codigosActivos, Usuario usuario) {

        Transferencia t = new Transferencia();
        t.setResponsableOrigen(respOrigen);
        t.setFechaTransferencia(fTransf);
        t.setEstado("A");
        t.setRegistroIdUsuario(usuario.getIdUsuario());
        t.setResponsableDestino(respDestino);
        t.setFechaRecepcion(fRecep);

        for (int i = 0; i < codigosActivos.size(); i++) {
            String codigo      = safe(codigosActivos.get(i));
            String ubiOrigen   = safe(ubicacionesOrigen.get(i));
            String ubiActual   = safe(ubicacionesActuales.get(i));

            Activo a = activoService.findByCodigo(codigo)
                .orElseThrow(() -> new IllegalArgumentException("Activo no encontrado: " + codigo));

            TransferenciaDetalle d = new TransferenciaDetalle();
            d.setTransferencia(t);
            d.setActivo(a);

            d.setOficinaAnterior(a.getOficina());
            d.setResponsableAnterior(a.getResponsable());

            // NUEVO: guarda ubicaciones declaradas
            d.setUbicacionOrigen(ubiOrigen);
            d.setUbicacionActual(ubiActual);

            d.setEstado("A");
            d.setRegistroIdUsuario(usuario != null ? usuario.getIdUsuario() : null);

            // Opcional: mover el activo al destino / PERO TENDRA QUE PRIMEOR VALIDAR LA LIC VERO
            // Oficina oficinaActualizar = oficinaService.buscarPorNombre(ubiActual);
            // a.setOficina(oficinaActualizar);
            // a.setResponsable(respDestino);

            t.getDetalles().add(d);
        }

        return transferenciaService.save(t);
    }

    private String safe(String s) {
        return s == null ? "" : s.trim();
    }
}
