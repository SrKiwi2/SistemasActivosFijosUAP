package com.usic.SistemasActivosFijosUAP.model.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;

import org.springframework.stereotype.Service;

import com.usic.SistemasActivosFijosUAP.model.IService.IActivoService;
import com.usic.SistemasActivosFijosUAP.model.IService.IOficinaService;
import com.usic.SistemasActivosFijosUAP.model.IService.ITransferenciaService;
import com.usic.SistemasActivosFijosUAP.model.dao.IHistorialActivoDao;
import com.usic.SistemasActivosFijosUAP.model.entity.Activo;
import com.usic.SistemasActivosFijosUAP.model.entity.HistorialActivo;
import com.usic.SistemasActivosFijosUAP.model.entity.Oficina;
import com.usic.SistemasActivosFijosUAP.model.entity.Responsable;
import com.usic.SistemasActivosFijosUAP.model.entity.Transferencia;
import com.usic.SistemasActivosFijosUAP.model.entity.TransferenciaDetalle;
import com.usic.SistemasActivosFijosUAP.model.entity.Usuario;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransferenciaService {

    private final ITransferenciaService transferenciaService;
    private final IActivoService activoService;
    private final IHistorialActivoDao historialActivoDao;

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

    /* NUEVA CONFIGURACION DE REGISTRO DE TRANSFERENCIA E HISTORIAL */
    @Transactional
    public Transferencia registrarTransferencia(
            List<ActivoConOrigen> activosConEstadoAnterior,
            String tipo,
            Oficina ofDestino,
            Responsable respDestino,
            Long idUsuario,
            String nombreUsuario) {
 
        LocalDate hoy = LocalDate.now();
 
        // 1. Cabecera
        Transferencia trf = new Transferencia();
        trf.setTipo(tipo);
        trf.setFechaTransferencia(hoy);
        trf.setEstadoProceso("COMPLETADA");
        trf.setOficinaDestino(ofDestino);
        trf.setResponsableDestino(respDestino);
        trf.setRegistroIdUsuario(idUsuario);
        trf.setRegistro(new Date());
 
        // Origen: tomamos el primer activo como referencia del origen
        // (en una transferencia interna todos vienen del mismo predio/oficina)
        ActivoConOrigen primero = activosConEstadoAnterior.get(0);
        trf.setOficinaOrigen(primero.oficinaAnterior);
        trf.setResponsableOrigen(primero.responsableAnterior != null
                                 ? primero.responsableAnterior : respDestino);
 
        // 2. Detalle + historial por cada activo
        for (ActivoConOrigen ac : activosConEstadoAnterior) {
            Activo a = ac.activo;
 
            // Detalle
            TransferenciaDetalle det = new TransferenciaDetalle();
            det.setActivo(a);
            det.setCodigoActivo(a.getCodigo());
            det.setDescripcionActivo(a.getDescripcion());
            det.setCostoActivo(a.getCosto() != null ? BigDecimal.valueOf(a.getCosto()) : null);
            det.setOficinaAnterior(ac.oficinaAnterior);
            det.setResponsableAnterior(ac.responsableAnterior);
            det.setOficinaDestino(ofDestino);
            det.setResponsableDestino(respDestino);
            det.setRegistro(new Date());
            det.setRegistroIdUsuario(idUsuario);
            trf.addDetalle(det);
 
            // Historial
            String tipoEvento = "INTERNA".equals(tipo) ? "TRANSFERENCIA_INT" : "TRANSFERENCIA_EXT";
            String desc = String.format(
                "Activo transferido de '%s' → '%s' | Resp: '%s' → '%s' | Por: %s",
                ac.officinaAnteriorNombre(),
                ofDestino.getNombre(),
                ac.respAnteriorNombre(),
                respDestino.getPersona().getNombreCompleto(),
                nombreUsuario
            );
 
            HistorialActivo hist = HistorialActivo.crear(
                a, tipoEvento,
                ac.oficinaAnterior, ac.responsableAnterior,
                ofDestino, respDestino,
                trf,                          // se asigna después del save
                desc,
                idUsuario, nombreUsuario
            );
            historialActivoDao.save(hist);
        }
 
        // 3. Persistir (el trigger SQL genera el número de transferencia)
        Transferencia saved = transferenciaService.save(trf);
        log.info("Transferencia {} registrada — {} activos", saved.getNumeroTransferencia(),
                 activosConEstadoAnterior.size());
 
        // 4. Actualizar la referencia en el historial recién guardado
        historialActivoDao.findByActivoIdActivoOrderByFechaEventoDesc(
            activosConEstadoAnterior.stream().mapToLong(ac -> ac.activo.getIdActivo()).findFirst().orElse(0L)
        ).stream().filter(h -> h.getTransferencia() == null).forEach(h -> {
            h.setTransferencia(saved);
            historialActivoDao.save(h);
        });
 
        return saved;
    }

    @Transactional
    public void registrarHistorial(
            Activo activo,
            String tipoEvento,
            Oficina ofAnterior, Responsable respAnterior,
            Oficina ofNueva,    Responsable respNuevo,
            String descripcion,
            Long idUsuario, String nombreUsuario) {
 
        HistorialActivo h = HistorialActivo.crear(
            activo, tipoEvento,
            ofAnterior, respAnterior,
            ofNueva, respNuevo,
            null, descripcion,
            idUsuario, nombreUsuario
        );
        historialActivoDao.save(h);
    }

    public static class ActivoConOrigen {
        public final Activo      activo;
        public final Oficina     oficinaAnterior;
        public final Responsable responsableAnterior;
 
        public ActivoConOrigen(Activo a) {
            this.activo               = a;
            this.oficinaAnterior      = a.getOficina();
            this.responsableAnterior  = a.getResponsable();
        }
 
        public String officinaAnteriorNombre() {
            return oficinaAnterior != null ? oficinaAnterior.getNombre() : "Sin oficina";
        }
 
        public String respAnteriorNombre() {
            return responsableAnterior != null ? responsableAnterior.getPersona().getNombreCompleto() : "Sin responsable";
        }
    }
}
