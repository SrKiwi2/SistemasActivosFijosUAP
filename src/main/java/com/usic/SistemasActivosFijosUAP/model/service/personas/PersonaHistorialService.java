package com.usic.SistemasActivosFijosUAP.model.service.personas;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.usic.SistemasActivosFijosUAP.model.dao.IPersonaCambioDao;
import com.usic.SistemasActivosFijosUAP.model.entity.Cargo;
import com.usic.SistemasActivosFijosUAP.model.entity.Persona;
import com.usic.SistemasActivosFijosUAP.model.entity.PersonaCambio;
import com.usic.SistemasActivosFijosUAP.model.entity.Usuario;

import lombok.RequiredArgsConstructor;

/**
 * Historial de cambios de personas ({@link PersonaCambio}): lo escriben «Personas y cargos»
 * y las ediciones de nombre / C.I. desde Personas y Responsables.
 */
@Service
@RequiredArgsConstructor
public class PersonaHistorialService {

    private static final Logger log = LoggerFactory.getLogger(PersonaHistorialService.class);
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    public static final String ORIGEN_PERSONAS_CARGOS = "PERSONAS_CARGOS";
    public static final String ORIGEN_PERSONA = "PERSONA";
    public static final String ORIGEN_RESPONSABLE = "RESPONSABLE";
    public static final String ORIGEN_HISTORIAL = "HISTORIAL";

    private final IPersonaCambioDao dao;
    private final ObjectMapper mapper = new ObjectMapper();

    public static String nuevoLote() {
        return UUID.randomUUID().toString();
    }

    /** Foto de la persona para el antes / después: nombre y C.I. */
    public static Map<String, Object> foto(Persona p) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (p == null) return m;
        m.put("idPersona", p.getIdPersona());
        m.put("nombre", p.getNombre());
        m.put("paterno", p.getPaterno());
        m.put("materno", p.getMaterno());
        m.put("ci", p.getCi());
        return m;
    }

    /**
     * Cambio de nombre y/o C.I. hecho fuera de «Personas y cargos» (Personas, Responsables).
     * No falla nunca: el historial no debe impedir guardar el cambio.
     */
    public void registrarDatos(Map<String, Object> antes, Persona despues, Usuario autor, String origen) {
        try {
            PersonaCambio c = nuevo(nuevoLote(), despues.getIdPersona(), PersonaCambio.TIPO_DATOS, autor, origen);
            c.setReferencia(nombreDe(despues));
            c.setAntes(json(antes));
            c.setDespues(json(foto(despues)));
            dao.save(c);
        } catch (Exception e) {
            log.warn("[PERSONAS] No se pudo anotar el cambio de la persona {} en el historial: {}",
                    despues == null ? null : despues.getIdPersona(), e.getMessage());
        }
    }

    /**
     * Una fila de responsable pasó de una persona a otra fuera del módulo (Responsables:
     * se le puso el C.I. de una persona que ya existía). No falla nunca.
     */
    public void registrarPaseDeFila(Long idResponsable, String referencia, Persona antes, Persona despues,
                                    Cargo cargo, Usuario autor, String origen) {
        try {
            PersonaCambio c = nuevo(nuevoLote(), despues.getIdPersona(), PersonaCambio.TIPO_SEPARACION, autor, origen);
            c.setIdResponsable(idResponsable);
            c.setReferencia(referencia);
            c.setAntes(json(fila(antes, cargo == null ? null : cargo.getNombre(), cargo == null ? null : cargo.getIdCargo())));
            c.setDespues(json(fila(despues, cargo == null ? null : cargo.getNombre(), cargo == null ? null : cargo.getIdCargo())));
            dao.save(c);
        } catch (Exception e) {
            log.warn("[PERSONAS] No se pudo anotar el pase de la fila {} en el historial: {}", idResponsable, e.getMessage());
        }
    }

    /** El cargo de una fila cambió porque se cambió en otra oficina de la misma persona. No falla nunca. */
    public void registrarCargoDeFila(String lote, Long idResponsable, String referencia, Persona persona,
                                     Long idCargoAntes, String cargoAntes, Cargo cargoDespues, Usuario autor, String origen) {
        try {
            PersonaCambio c = nuevo(lote, persona.getIdPersona(), PersonaCambio.TIPO_CARGO, autor, origen);
            c.setIdResponsable(idResponsable);
            c.setReferencia(referencia);
            c.setAntes(json(fila(persona, cargoAntes, idCargoAntes)));
            c.setDespues(json(fila(persona, cargoDespues.getNombre(), cargoDespues.getIdCargo())));
            dao.save(c);
        } catch (Exception e) {
            log.warn("[PERSONAS] No se pudo anotar el cargo de la fila {} en el historial: {}", idResponsable, e.getMessage());
        }
    }

    /** Mismo formato que las filas de «Personas y cargos» (idPersona, nomresp, ci, cargo, idCargo). */
    private static Map<String, Object> fila(Persona p, String cargo, Long idCargo) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("idPersona", p == null ? null : p.getIdPersona());
        m.put("nomresp", p == null ? null : nombreDe(p).replaceAll(" · C\\.I\\. .*$", ""));
        m.put("ci", p == null ? null : p.getCi());
        m.put("cargo", cargo);
        m.put("idCargo", idCargo);
        return m;
    }

    public PersonaCambio nuevo(String lote, Long idPersona, String tipo, Usuario autor, String origen) {
        PersonaCambio c = new PersonaCambio();
        c.setLote(lote);
        c.setIdPersona(idPersona);
        c.setTipo(tipo);
        c.setOrigen(origen);
        c.setFecha(LocalDateTime.now());
        if (autor != null) {
            c.setIdUsuario(autor.getIdUsuario());
            c.setUsuario(autor.getUsuario());
        }
        return c;
    }

    public PersonaCambio guardar(PersonaCambio c) {
        return dao.save(c);
    }

    public List<PersonaCambio> guardarTodos(List<PersonaCambio> l) {
        return dao.saveAll(l);
    }

    /** Historial para la pantalla, lo más nuevo primero. */
    public List<Map<String, Object>> deLasPersonas(Collection<Long> ids, int max) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (ids == null || ids.isEmpty()) return out;
        for (PersonaCambio c : dao.dePersonas(ids, PageRequest.of(0, max))) out.add(aMapa(c));
        return out;
    }

    public String json(Object o) {
        try {
            return mapper.writeValueAsString(o);
        } catch (Exception e) {
            return String.valueOf(o);
        }
    }

    /** Entradas de un lote, en el orden en que se escribieron. */
    public List<PersonaCambio> registrosDelLote(String lote) {
        return dao.findByLoteOrderByIdCambioAsc(lote);
    }

    /** Un lote para la pantalla (mismo formato que {@link #deLasPersonas}). */
    public List<Map<String, Object>> delLote(String lote) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (PersonaCambio c : dao.findByLoteOrderByIdCambioAsc(lote)) out.add(aMapa(c));
        return out;
    }

    public Map<String, Object> leerJson(String json) {
        return leer(json);
    }

    private Map<String, Object> aMapa(PersonaCambio c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getIdCambio());
        m.put("lote", c.getLote());
        m.put("fecha", c.getFecha() == null ? "" : c.getFecha().format(FMT));
        m.put("usuario", c.getUsuario());
        m.put("tipo", c.getTipo());
        m.put("idResponsable", c.getIdResponsable());
        m.put("referencia", c.getReferencia());
        m.put("antes", leer(c.getAntes()));
        m.put("despues", leer(c.getDespues()));
        m.put("motivo", c.getMotivo());
        m.put("origen", c.getOrigen());
        m.put("estadoVsiaf", c.getEstadoVsiaf());
        m.put("detalleVsiaf", c.getDetalleVsiaf());
        m.put("idSolicitud", c.getIdSolicitud());
        m.put("loteRevertido", c.getLoteRevertido());
        m.put("revertidoPor", c.getRevertidoPor());
        return m;
    }

    private Map<String, Object> leer(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return mapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return Map.of("texto", json);
        }
    }

    private static String nombreDe(Persona p) {
        return String.join(" ", p.getNombre() == null ? "" : p.getNombre(),
                p.getPaterno() == null ? "" : p.getPaterno(), p.getMaterno() == null ? "" : p.getMaterno())
                .replaceAll("\\s+", " ").trim() + (p.getCi() == null ? "" : " · C.I. " + p.getCi());
    }
}
