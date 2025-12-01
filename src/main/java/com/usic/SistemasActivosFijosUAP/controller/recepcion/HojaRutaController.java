package com.usic.SistemasActivosFijosUAP.controller.recepcion;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.usic.SistemasActivosFijosUAP.anotacion.ValidarUsuarioAutenticado;
import com.usic.SistemasActivosFijosUAP.model.IService.IHojaRutaService;
import com.usic.SistemasActivosFijosUAP.model.IService.IMovimientoService;
import com.usic.SistemasActivosFijosUAP.model.IService.ISolictanteService;
import com.usic.SistemasActivosFijosUAP.model.IService.IUnidadService;
import com.usic.SistemasActivosFijosUAP.model.entity.HojaRuta;
import com.usic.SistemasActivosFijosUAP.model.entity.Movimiento;
import com.usic.SistemasActivosFijosUAP.model.entity.Solicitante;
import com.usic.SistemasActivosFijosUAP.model.entity.Unidad;
import com.usic.SistemasActivosFijosUAP.model.entity.Usuario;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/administracion/hoja-ruta")
@RequiredArgsConstructor
public class HojaRutaController {
    
    private final IHojaRutaService hojaRutaService;
    private final IMovimientoService movimientoService;
    private final ISolictanteService solicitanteService;
    private final IUnidadService unidadService;

    @ValidarUsuarioAutenticado
    @GetMapping("/vista")
    public String inicio(Model model, HttpServletRequest request) {

        // Validar que el usuario tenga permiso
        HttpSession session = request.getSession(false);
        Usuario usuario = (Usuario) (session != null ? session.getAttribute("usuario") : null);
        
        if (usuario != null) {
            String rol = usuario.getRol().getNombre().toUpperCase();
            
            // Solo RECEPCION y ADMINISTRADOR pueden acceder
            if (!rol.equals("RECEPCION") && !rol.equals("ADMINISTRADOR")) {
                return "redirect:/acceso-denegado";
            }
        }

        model.addAttribute("listaSolicitantes", solicitanteService.findAll());
        model.addAttribute("listaUnidades", unidadService.findAll());
        return "hojaRuta/vista";
    }

    // Buscar hoja de ruta por código
    @ValidarUsuarioAutenticado
    @PostMapping("/buscar")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> buscarHojaRuta(
            @RequestParam("codigo") String codigo,
            @RequestParam("tipo") String tipo,
            @RequestParam("gestion") Integer gestion) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            Optional<HojaRuta> hojaRutaOpt = hojaRutaService.findByCodigo(codigo);
            
            if (hojaRutaOpt.isEmpty()) {
                response.put("ok", false);
                response.put("msg", "No se encontró la hoja de ruta con el código: " + codigo);
                return ResponseEntity.ok(response);
            }
            
            HojaRuta hojaRuta = hojaRutaOpt.get();
            
            // Verificar que coincida el tipo y gestión
            if (!hojaRuta.getTipo().equals(tipo) || !hojaRuta.getGestion().equals(gestion)) {
                response.put("ok", false);
                response.put("msg", "La hoja de ruta no coincide con el tipo o gestión especificados");
                return ResponseEntity.ok(response);
            }
            
            // Obtener movimientos
            List<Movimiento> movimientos = movimientoService.findByHojaRuta(hojaRuta.getIdHojaRuta());
            
            // Obtener movimiento actual (último)
            Movimiento movimientoActual = movimientos.isEmpty() ? null : movimientos.get(0);
            
            // Construir respuesta
            Map<String, Object> hojaRutaData = new HashMap<>();
            hojaRutaData.put("idHojaRuta", hojaRuta.getIdHojaRuta());
            hojaRutaData.put("codigo", hojaRuta.getCodigo());
            hojaRutaData.put("tipo", hojaRuta.getTipo());
            hojaRutaData.put("descripcion", hojaRuta.getDescripcion());
            hojaRutaData.put("certificacion", hojaRuta.getCertificacion());
            hojaRutaData.put("monto", hojaRuta.getMonto());
            hojaRutaData.put("gestion", hojaRuta.getGestion());
            hojaRutaData.put("solicitanteNombre", hojaRuta.getSolicitante().getNombre());
            hojaRutaData.put("solicitanteCargo", hojaRuta.getSolicitante().getCargo());
            hojaRutaData.put("solicitanteId", hojaRuta.getSolicitante().getIdSolicitante());
            
            List<Map<String, Object>> movimientosData = new ArrayList<>();
            for (Movimiento m : movimientos) {
                Map<String, Object> movData = new HashMap<>();
                movData.put("idMovimiento", m.getIdMovimiento());
                String estadoNumero = m.getEstadoMovimiento() != null ? m.getEstadoMovimiento() : "1";
                String estadoTexto = convertirEstadoNumeroATexto(estadoNumero);
                movData.put("estado", estadoTexto);
                movData.put("fecha", m.getFecha() != null ? m.getFecha().toString() : "");
                movData.put("hora", m.getHora() != null ? m.getHora().toString() : "00:00:00");
                movData.put("origen", m.getUnidadOrigen() != null ? m.getUnidadOrigen().getNombre() : "SIN ORIGEN");
                movData.put("destino", m.getUnidadDestino() != null ? m.getUnidadDestino().getNombre() : "SIN DESTINO");
                movData.put("observacion", m.getObservacion() != null ? m.getObservacion() : "");
               
                //edit
                movData.put("unidadOrigenId", m.getUnidadOrigen() != null ? m.getUnidadOrigen().getIdUnidad() : null);
                movData.put("unidadDestinoId", m.getUnidadDestino() != null ? m.getUnidadDestino().getIdUnidad() : null);
                movimientosData.add(movData);
            }
            
            response.put("ok", true);
            response.put("hojaRuta", hojaRutaData);
            response.put("movimientos", movimientosData);
            String estadoActual = "SIN MOVIMIENTOS";
            if (movimientoActual != null && movimientoActual.getEstadoMovimiento() != null) {
                estadoActual = convertirEstadoNumeroATexto(movimientoActual.getEstadoMovimiento());
            }
            response.put("movimientoActual", estadoActual);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("ok", false);
            response.put("msg", "Error al buscar: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    private String convertirEstadoNumeroATexto(String estadoNumero) {
        switch (estadoNumero) {
            case "1": return "RECIBIDO";
            case "2": return "ENVIADO";
            case "3": return "ARCHIVADO";
            default: return "DESCONOCIDO";
        }
    }

    private String convertirEstadoFormularioANumero(Integer estadoFormulario) {
        switch (estadoFormulario) {
            case 1: return "1"; // RECIBIR
            case 2: return "2"; // ENVIAR
            case 3: return "3"; // ARCHIVAR
            default: return "1";
        }
    }

    private Integer convertirEstadoNumeroAFormulario(String estadoNumero) {
        switch (estadoNumero) {
            case "1": return 1;
            case "2": return 2;
            case "3": return 3;
            default: return 1;
        }
    }

    // Registrar nueva hoja de ruta
    @ValidarUsuarioAutenticado
    @PostMapping("/registrar")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> registrar(
            HttpServletRequest request,
            @RequestParam("tipo") String tipo,
            @RequestParam("codigo") String codigo,
            @RequestParam("gestion") Integer gestion,
            @RequestParam("solicitanteId") Long solicitanteId,
            @RequestParam("descripcion") String descripcion,
            @RequestParam(value = "certificacion", required = false) String certificacion,
            @RequestParam(value = "monto", required = false) BigDecimal monto,
            @RequestParam("unidadOrigenId") Long unidadOrigenId,
            @RequestParam("fecha") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            @RequestParam("hora") @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime hora) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            // Verificar que no exista el código
            if (hojaRutaService.findByCodigo(codigo).isPresent()) {
                response.put("ok", false);
                response.put("msg", "Ya existe una hoja de ruta con ese código");
                return ResponseEntity.ok(response);
            }
            
            // Obtener entidades
            Solicitante solicitante = solicitanteService.findById(solicitanteId);
            Unidad unidadOrigen = unidadService.findById(unidadOrigenId);
            
            if (solicitante == null || unidadOrigen == null) {
                response.put("ok", false);
                response.put("msg", "Solicitante o unidad no encontrados");
                return ResponseEntity.badRequest().body(response);
            }
            
            // Crear hoja de ruta
            HojaRuta hojaRuta = new HojaRuta();
            hojaRuta.setTipo(tipo);
            hojaRuta.setCodigo(codigo);
            hojaRuta.setGestion(gestion);
            hojaRuta.setSolicitante(solicitante);
            hojaRuta.setDescripcion(descripcion);
            hojaRuta.setCertificacion(certificacion);
            hojaRuta.setMonto(monto);
            hojaRuta.setEstado("ACTIVO");
            
            // Auditoría
            Usuario usuarioLogueado = (Usuario) request.getSession().getAttribute("usuario");
            hojaRuta.setRegistroIdUsuario(usuarioLogueado.getIdUsuario());
            
            // Guardar
            hojaRutaService.save(hojaRuta);
            
            // Crear primer movimiento (RECIBIR)
            Movimiento primerMovimiento = new Movimiento();
            primerMovimiento.setHojaRuta(hojaRuta);
            primerMovimiento.setFecha(fecha);
            primerMovimiento.setHora(hora);
            primerMovimiento.setEstadoMovimiento("1");
            primerMovimiento.setSolicitante(solicitante);
            primerMovimiento.setUnidadOrigen(unidadOrigen);
            primerMovimiento.setUnidadDestino(unidadOrigen); // Mismo origen al inicio
            primerMovimiento.setObservacion("Registro inicial");
            primerMovimiento.setEstado("ACTIVO");
            primerMovimiento.setRegistroIdUsuario(usuarioLogueado.getIdUsuario());
            
            movimientoService.save(primerMovimiento);
            
            response.put("ok", true);
            response.put("msg", "Hoja de ruta registrada correctamente");
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            e.printStackTrace();
            response.put("ok", false);
            response.put("msg", "Error al registrar: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    // Modificar hoja de ruta
    @ValidarUsuarioAutenticado
    @PostMapping("/modificar")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> modificar(
            HttpServletRequest request,
            @RequestParam("idHojaRuta") Long idHojaRuta,
            @RequestParam("tipo") String tipo,
            @RequestParam("codigo") String codigo,
            @RequestParam("gestion") Integer gestion,
            @RequestParam("solicitanteId") Long solicitanteId,
            @RequestParam("descripcion") String descripcion,
            @RequestParam(value = "certificacion", required = false) String certificacion,
            @RequestParam(value = "monto", required = false) BigDecimal monto) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            HojaRuta hojaRuta = hojaRutaService.findById(idHojaRuta);
            
            if (hojaRuta == null) {
                response.put("ok", false);
                response.put("msg", "Hoja de ruta no encontrada");
                return ResponseEntity.badRequest().body(response);
            }
            
            // Verificar código único si cambió
            if (!hojaRuta.getCodigo().equals(codigo) && hojaRutaService.findByCodigo(codigo).isPresent()) {
                response.put("ok", false);
                response.put("msg", "Ya existe una hoja de ruta con ese código");
                return ResponseEntity.ok(response);
            }
            
            Solicitante solicitante = solicitanteService.findById(solicitanteId);
            
            // Actualizar
            hojaRuta.setTipo(tipo);
            hojaRuta.setCodigo(codigo);
            hojaRuta.setGestion(gestion);
            hojaRuta.setSolicitante(solicitante);
            hojaRuta.setDescripcion(descripcion);
            hojaRuta.setCertificacion(certificacion);
            hojaRuta.setMonto(monto);
            
            // Auditoría
            Usuario usuarioLogueado = (Usuario) request.getSession().getAttribute("usuario");
            hojaRuta.setModificacionIdUsuario(usuarioLogueado.getIdUsuario());
            
            hojaRutaService.save(hojaRuta);
            
            response.put("ok", true);
            response.put("msg", "Hoja de ruta modificada correctamente");
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            e.printStackTrace();
            response.put("ok", false);
            response.put("msg", "Error al modificar: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    // Registrar/Modificar movimiento
    @ValidarUsuarioAutenticado
    @PostMapping("/movimiento/guardar")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> guardarMovimiento(
            HttpServletRequest request,
            @RequestParam(value = "idMovimiento", required = false) Long idMovimiento,
            @RequestParam("hojaRutaId") Long hojaRutaId,
            @RequestParam("estado") Integer estadoNumero,
            @RequestParam("fecha") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            @RequestParam("hora") @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime hora,
            @RequestParam("unidadOrigenId") Long unidadOrigenId,
            @RequestParam("unidadDestinoId") Long unidadDestinoId,
            @RequestParam(value = "observacion", required = false) String observacion) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            // Convertir estado numérico a texto
           String estadoParaGuardar = convertirEstadoFormularioANumero(estadoNumero);
            
            Usuario usuarioLogueado = (Usuario) request.getSession().getAttribute("usuario");
            
            if (idMovimiento != null) {
                // Modificar movimiento existente
                Movimiento movimiento = movimientoService.findById(idMovimiento);
                
                if (movimiento == null) {
                    response.put("ok", false);
                    response.put("msg", "Movimiento no encontrado");
                    return ResponseEntity.badRequest().body(response);
                }
                
                movimiento.setEstadoMovimiento(estadoParaGuardar);
                movimiento.setFecha(fecha);
                movimiento.setHora(hora);
                movimiento.setUnidadOrigen(unidadService.findById(unidadOrigenId));
                movimiento.setUnidadDestino(unidadService.findById(unidadDestinoId));
                movimiento.setObservacion(observacion);
                movimiento.setModificacionIdUsuario(usuarioLogueado.getIdUsuario());
                
                movimientoService.save(movimiento);
                
                response.put("ok", true);
                response.put("msg", "Movimiento modificado correctamente");
                
            } else {
                // Nuevo movimiento
                HojaRuta hojaRuta = hojaRutaService.findById(hojaRutaId);

                if (hojaRuta == null) {
                    response.put("ok", false);
                    response.put("msg", "Hoja de ruta no encontrada");
                    return ResponseEntity.badRequest().body(response);
                }
                
                Movimiento movimiento = new Movimiento();
                movimiento.setHojaRuta(hojaRuta);
                movimiento.setEstadoMovimiento(estadoParaGuardar);
                movimiento.setFecha(fecha);
                movimiento.setHora(hora);
                movimiento.setSolicitante(hojaRuta.getSolicitante());
                movimiento.setUnidadOrigen(unidadService.findById(unidadOrigenId));
                movimiento.setUnidadDestino(unidadService.findById(unidadDestinoId));
                movimiento.setObservacion(observacion);
                movimiento.setEstado("ACTIVO");
                movimiento.setRegistroIdUsuario(usuarioLogueado.getIdUsuario());
                
                movimientoService.save(movimiento);
                
                response.put("ok", true);
                response.put("msg", "Movimiento registrado correctamente");
            }
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            e.printStackTrace();
            response.put("ok", false);
            response.put("msg", "Error al guardar movimiento: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    // Obtener datos de movimiento para editar
    @ValidarUsuarioAutenticado
    @GetMapping("/movimiento/{id}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> obtenerMovimiento(@PathVariable("id") Long idMovimiento) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            Movimiento movimiento = movimientoService.findById(idMovimiento);
            
            if (movimiento == null) {
                response.put("ok", false);
                response.put("msg", "Movimiento no encontrado");
                return ResponseEntity.badRequest().body(response);
            }

            String estadoGuardado = movimiento.getEstadoMovimiento() != null ? movimiento.getEstadoMovimiento() : "1";
            Integer estadoNumero = convertirEstadoNumeroAFormulario(estadoGuardado);
            String estadoTexto = convertirEstadoNumeroATexto(estadoGuardado);
            
            Map<String, Object> movData = new HashMap<>();
            movData.put("idMovimiento", movimiento.getIdMovimiento());
            movData.put("estado", estadoTexto);
            movData.put("estadoNumero", estadoNumero);
            movData.put("fecha", movimiento.getFecha() != null ? movimiento.getFecha().toString() : "");
            movData.put("hora", movimiento.getHora() != null ? movimiento.getHora().toString() : "");
            movData.put("unidadOrigenId", movimiento.getUnidadOrigen() != null ? movimiento.getUnidadOrigen().getIdUnidad() : null);
            movData.put("unidadDestinoId", movimiento.getUnidadDestino() != null ? movimiento.getUnidadDestino().getIdUnidad() : null);
            movData.put("observacion", movimiento.getObservacion() != null ? movimiento.getObservacion() : "");
            
            response.put("ok", true);
            response.put("movimiento", movData);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("ok", false);
            response.put("msg", "Error: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
}
