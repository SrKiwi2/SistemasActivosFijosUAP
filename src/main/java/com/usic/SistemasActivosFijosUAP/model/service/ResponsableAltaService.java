package com.usic.SistemasActivosFijosUAP.model.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.usic.SistemasActivosFijosUAP.model.IService.ICargoService;
import com.usic.SistemasActivosFijosUAP.model.IService.IOficinaService;
import com.usic.SistemasActivosFijosUAP.model.IService.IPersonaService;
import com.usic.SistemasActivosFijosUAP.model.IService.IResponsableService;
import com.usic.SistemasActivosFijosUAP.model.entity.Cargo;
import com.usic.SistemasActivosFijosUAP.model.entity.Oficina;
import com.usic.SistemasActivosFijosUAP.model.entity.Persona;
import com.usic.SistemasActivosFijosUAP.model.entity.Responsable;
import com.usic.SistemasActivosFijosUAP.model.entity.Usuario;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Alta de un responsable (persona + cargo + responsable) en PostgreSQL.
 * <p>
 * Vivía dentro de {@code ResponsableController.registrarResponsable}; se trajo acá para
 * poder usarla también desde el alta de oficina (que ahora puede registrar a su
 * responsable en el mismo paso) y para que sea atómica: antes la persona se guardaba
 * aunque después fallara el responsable, y quedaba huérfana.
 * <p>
 * No toca el VSIAF: eso lo hace quien llama, con {@link VsiafApoyoService}, recién
 * cuando la transacción ya confirmó.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResponsableAltaService {

    /** Largos de RESP.DBF: lo que no entra, el VSIAF lo recorta o lo rechaza. */
    public static final int MAX_CI = 15;
    public static final int MAX_CARGO = 40;
    /** NOMRESP: nombre y apellidos viajan juntos en un solo campo. */
    public static final int MAX_NOMBRE_COMPLETO = 35;
    public static final int MAX_COD_RESP = 99999;

    private final IResponsableService responsableService;
    private final IPersonaService personaService;
    private final ICargoService cargoService;
    private final IOficinaService oficinaService;

    /** Datos del formulario de alta. */
    public record DatosAlta(String ci, Short codExp, String codigoFuncionario, String codigoApi,
                            String nombre, String paterno, String materno, String correo, String cargo) {}

    public record ResultadoAlta(Responsable responsable, boolean personaNueva) {}

    /**
     * Hay personas registradas con el mismo nombre y ninguna con el CI indicado. Puede ser
     * la misma persona cargada sin CI o alguien distinto: se le pregunta al usuario antes
     * de crear otra.
     */
    public static class PersonasSimilaresException extends RuntimeException {
        private final transient List<Persona> personas;

        public PersonasSimilaresException(List<Persona> personas) {
            super("Se encontraron personas con un nombre similar.");
            this.personas = personas;
        }

        /** Cuerpo del 409 que ya entienden los formularios (msg + personasCoincidentes). */
        public Map<String, Object> cuerpo() {
            StringBuilder msg = new StringBuilder("Se encontraron personas similares:\n");
            List<Map<String, Object>> lista = new ArrayList<>();
            for (Persona p : personas.stream().limit(10).toList()) {
                msg.append(String.format("- %s (CI: %s)\n", p.getNombreCompleto(),
                        p.getCi() != null ? p.getCi() : "Sin CI"));
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("idPersona", p.getIdPersona());
                m.put("nombreCompleto", p.getNombreCompleto());
                m.put("ci", p.getCi() != null ? p.getCi() : "");
                m.put("correo", p.getCorreo() != null ? p.getCorreo() : "");
                lista.add(m);
            }
            msg.append("\n¿Desea continuar creando una nueva persona?");

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("ok", false);
            body.put("msg", msg.toString());
            body.put("personasCoincidentes", lista);
            body.put("requireConfirmacion", true);
            return body;
        }
    }

    /**
     * Registra el responsable en la oficina indicada.
     *
     * @param forzarPersonaNueva el usuario ya vio las personas similares y confirmó crear otra
     * @param pendienteDbf       true si el responsable no se va a enviar al VSIAF ahora
     *                           (alta rápida desde otros módulos: viaja al aprobar el activo)
     * @throws IllegalArgumentException   dato inválido o duplicado (mensaje para el usuario)
     * @throws PersonasSimilaresException hay que confirmar antes de crear la persona
     */
    @Transactional
    public ResultadoAlta registrar(DatosAlta d, Oficina oficina, Usuario usuario,
                                   boolean forzarPersonaNueva, boolean pendienteDbf) {
        if (oficina == null) throw new IllegalArgumentException("No se encontró la oficina especificada.");

        String ci = limpiar(d.ci());
        String codFun = limpiar(d.codigoFuncionario());
        String nombre = mayus(d.nombre());
        String paterno = mayus(d.paterno());
        String materno = mayus(d.materno());
        String cargoNombre = mayus(d.cargo());

        validar(ci, codFun, cargoNombre);
        validarNombreCompleto(nombre, paterno, materno);

        if (oficina.getIdOficina() != null
                && responsableService.findByCodigoFuncionarioYOficina(codFun, oficina.getIdOficina()) != null) {
            throw new IllegalArgumentException(String.format(
                    "Ya existe un responsable con código %s en la oficina %s.", codFun, oficina.getNombre()));
        }

        // ── Persona ───────────────────────────────────────────────────────
        Persona persona = (ci != null) ? personaService.buscarPersonaPorCI(ci) : null;

        if (persona == null) {
            if (nombre == null || paterno == null) {
                throw new IllegalArgumentException("La persona no está registrada: complete nombres y apellido paterno.");
            }
            Persona mismoNombre = personaService.buscarPersonaPorNombreCompletoUno(nombre, paterno, materno);
            if (mismoNombre != null && (mismoNombre.getCi() == null || mismoNombre.getCi().isBlank())) {
                // Misma persona cargada sin CI (típico de la sincronización con el VSIAF): se completa.
                persona = mismoNombre;
                persona.setCi(ci);
                personaService.save(persona);
                log.info("Persona {} (sin CI) reutilizada y completada con CI {}", persona.getIdPersona(), ci);
            } else if (!forzarPersonaNueva) {
                // Con el mismo nombre pero otro CI es otra persona, pero conviene que el usuario lo vea.
                List<Persona> similares = new ArrayList<>();
                if (mismoNombre != null) similares.add(mismoNombre);
                List<Persona> otros = personaService.buscarPorNombreApellidos(nombre, paterno, materno);
                if (otros != null) {
                    for (Persona p : otros) {
                        if (similares.stream().noneMatch(s -> s.getIdPersona().equals(p.getIdPersona()))) {
                            similares.add(p);
                        }
                    }
                }
                if (!similares.isEmpty()) throw new PersonasSimilaresException(similares);
            }
        }

        if (persona != null && oficina.getIdOficina() != null
                && responsableService.existeResponsablePorPersonaYOficina(persona.getIdPersona(), oficina.getIdOficina())) {
            throw new IllegalArgumentException(String.format("La persona %s ya es responsable en la oficina %s.",
                    persona.getNombreCompleto(), oficina.getNombre()));
        }

        boolean personaNueva = false;
        if (persona == null) {
            personaNueva = true;
            persona = new Persona();
            persona.setCi(ci);
            persona.setNombre(nombre);
            persona.setPaterno(paterno);
            persona.setMaterno(materno);
            persona.setCorreo(limpiar(d.correo()));
            persona.setEstado("ACTIVO");
            if (usuario != null) persona.setRegistroIdUsuario(usuario.getIdUsuario());
            personaService.save(persona);
        }

        // ── Responsable ───────────────────────────────────────────────────
        Responsable r = new Responsable();
        r.setCodigoApi(limpiar(d.codigoApi()));
        r.setCodigoFuncionario(codFun);
        r.setPersona(persona);
        r.setOficina(oficina);
        r.setCargo(cargoNombre != null ? obtenerCargo(cargoNombre, usuario) : null);
        r.setFechaUlt(LocalDate.now());
        r.setUsuario(usuario != null ? usuario.getUsuario() : "SISTEMA");
        r.setApiEstado(pendienteDbf ? Short.valueOf("3") : Short.valueOf("1"));
        r.setPendienteDbf(pendienteDbf);
        r.setCodExp(d.codExp() != null ? d.codExp() : Short.valueOf("9"));
        r.setEstado("ACTIVO");
        if (usuario != null) r.setRegistroIdUsuario(usuario.getIdUsuario());
        responsableService.save(r);

        log.info("Responsable {} registrado en oficina {} (persona {}{})", r.getIdResponsable(),
                oficina.getIdOficina(), persona.getIdPersona(), personaNueva ? ", nueva" : "");
        return new ResultadoAlta(r, personaNueva);
    }

    /**
     * Registra una oficina nueva junto con su primer responsable, todo o nada: si el
     * responsable no pasa la validación (o hay que confirmar la persona), la oficina
     * tampoco queda guardada.
     */
    @Transactional
    public ResultadoAlta registrarOficinaConResponsable(Oficina oficina, DatosAlta d, Usuario usuario,
                                                        boolean forzarPersonaNueva) {
        // Se valida antes de guardar la oficina para no gastar un id en un intento fallido.
        validar(limpiar(d.ci()), limpiar(d.codigoFuncionario()), mayus(d.cargo()));
        oficinaService.save(oficina);
        return registrar(d, oficina, usuario, forzarPersonaNueva, false);
    }

    /** Cargo por nombre; si no existe se crea. */
    public Cargo obtenerCargo(String nombreCargo, Usuario usuario) {
        String nombre = mayus(nombreCargo);
        if (nombre == null) return null;
        Cargo cargo = cargoService.buscarPorNombre(nombre);
        if (cargo != null) return cargo;

        cargo = new Cargo();
        cargo.setNombre(nombre);
        cargo.setDescripcion("Cargo registrado desde el SCIAF");
        cargo.setEstado("ACTIVO");
        cargo.setRegistro(new Date());
        if (usuario != null) cargo.setRegistroIdUsuario(usuario.getIdUsuario());
        cargoService.save(cargo);
        return cargo;
    }

    /** Reglas que exige RESP.DBF; se aplican igual en el alta y en la edición. */
    public void validar(String ci, String codigoFuncionario, String cargo) {
        if (ci == null) throw new IllegalArgumentException("El C.I. es obligatorio.");
        if (ci.length() > MAX_CI) {
            throw new IllegalArgumentException("El C.I. no puede tener más de " + MAX_CI + " caracteres.");
        }
        if (codigoFuncionario == null || !codigoFuncionario.matches("\\d{1,5}")
                || Integer.parseInt(codigoFuncionario) < 1 || Integer.parseInt(codigoFuncionario) > MAX_COD_RESP) {
            throw new IllegalArgumentException("El código de funcionario debe ser un número entre 1 y " + MAX_COD_RESP + ".");
        }
        if (cargo == null) throw new IllegalArgumentException("El cargo es obligatorio.");
        if (cargo.length() > MAX_CARGO) {
            throw new IllegalArgumentException("El cargo no puede tener más de " + MAX_CARGO
                    + " caracteres (es el largo del campo en el VSIAF).");
        }
    }

    /**
     * Nombre + apellidos entran en NOMRESP, de 35 caracteres. Se valida en el alta: más
     * largo, el VSIAF lo recorta por su cuenta y deja de coincidir con el SCIAF. En la
     * edición de registros viejos no se bloquea, solo se avisa desde la pantalla.
     */
    public void validarNombreCompleto(String nombre, String paterno, String materno) {
        String completo = java.util.stream.Stream.of(mayus(nombre), mayus(paterno), mayus(materno))
                .filter(java.util.Objects::nonNull)
                .reduce((a, b) -> a + " " + b).orElse("");
        if (completo.length() > MAX_NOMBRE_COMPLETO) {
            throw new IllegalArgumentException(String.format(
                    "El nombre completo tiene %d caracteres y el VSIAF admite %d. Abrevie el nombre.",
                    completo.length(), MAX_NOMBRE_COMPLETO));
        }
    }

    public static String limpiar(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    public static String mayus(String s) {
        String t = limpiar(s);
        return t == null ? null : t.toUpperCase();
    }
}
