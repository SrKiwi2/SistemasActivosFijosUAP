package com.usic.SistemasActivosFijosUAP;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.usic.SistemasActivosFijosUAP.config.PermisoOpcionInterceptor;
import com.usic.SistemasActivosFijosUAP.model.IService.IOpcionMenuService;
import com.usic.SistemasActivosFijosUAP.model.entity.OpcionMenu;

/**
 * Caso real que motivó estas pruebas: a hector/carmen/mayko se les habilitó
 * Transferencia Interna/Externa y Consulta de Activos, pero los endpoints de esas
 * pantallas viven bajo /administracion/activo y /administracion/responsable. La única
 * forma de que funcionaran era darles "Registro de Activos", que además les aparecía
 * en el menú. Lo que se verifica acá es que puedan trabajar SIN ese permiso.
 */
class PermisoOpcionInterceptorTest {

    /** Los permisos que realmente tienen asignados hector/carmen/mayko. */
    private static final Set<String> PERMISOS_APOYO_TRANSFERENCIAS =
            Set.of("opcion_ta", "opcion_trInterna", "opcion_trExterna", "opcion_consulta_activo");

    private PermisoOpcionInterceptor interceptor() {
        IOpcionMenuService servicio = mock(IOpcionMenuService.class);
        when(servicio.listarItems()).thenReturn(List.of(
                item("opcion_activo", "/administracion/activo"),
                item("opcion_activop", "/administracion/activo/vistap"),
                item("opcion_responsable", "/administracion/responsable"),
                item("opcion_responsable_entrega", "/administracion/responsable-entrega"),
                item("opcion_oficina", "/administracion/oficina"),
                item("opcion_correlativo", "/administracion/correlativo"),
                item("opcion_consulta_activo", "/administracion/consulta"),
                item("opcion_aan", "/administracion/asignacion"),
                item("opcion_ActivoAj", "/administracion/asignar"),
                item("opcion_control_mapa", "/administracion/control-activos"),
                item("opcion_control_faltantes", "/administracion/control-activos/faltantes"),
                item("opcion_trHistorial", "/administracion/activo/transferencias/historial"),
                item("opcion_ta", "/administracion/transferencia"),
                item("opcion_trInterna", "/administracion/trasnferencia/trasnferenciaInterna"),
                item("opcion_trExterna", "/administracion/trasnferencia/trasnferenciaExterna")));
        return new PermisoOpcionInterceptor(servicio);
    }

    private OpcionMenu item(String codigo, String rutaBase) {
        OpcionMenu o = new OpcionMenu();
        o.setCodigo(codigo);
        o.setRutaBase(rutaBase);
        return o;
    }

    private boolean permite(Set<String> opciones, String uri) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", uri);
        request.getSession().setAttribute("opciones", opciones);
        return interceptor().preHandle(request, new MockHttpServletResponse(), new Object());
    }

    @Test
    void confirmarUnaTransferenciaNoExigeRegistroDeActivos() throws Exception {
        assertTrue(permite(PERMISOS_APOYO_TRANSFERENCIAS, "/administracion/activo/transferencia-masiva"));
    }

    @Test
    void registrarResponsableDesdeTransferenciaInternaNoExigeElModuloResponsables() throws Exception {
        assertTrue(permite(PERMISOS_APOYO_TRANSFERENCIAS, "/administracion/responsable/registrar-responsable"));
        assertTrue(permite(PERMISOS_APOYO_TRANSFERENCIAS, "/administracion/responsable/registrar-responsable-forzado"));
        assertTrue(permite(PERMISOS_APOYO_TRANSFERENCIAS, "/administracion/responsable/obtener-siguiente-codigo-funcionario"));
    }

    @Test
    void consultaDeActivosPuedeListarLaTabla() throws Exception {
        assertTrue(permite(PERMISOS_APOYO_TRANSFERENCIAS, "/administracion/activo/datatables"));
    }

    /** El punto de todo el cambio: acceso a lo suyo, y nada más. */
    @Test
    void sigueBloqueadoElModuloDeRegistroDeActivos() throws Exception {
        assertFalse(permite(PERMISOS_APOYO_TRANSFERENCIAS, "/administracion/activo/vista"));
        assertFalse(permite(PERMISOS_APOYO_TRANSFERENCIAS, "/administracion/activo/guardar"));
        assertFalse(permite(PERMISOS_APOYO_TRANSFERENCIAS, "/administracion/responsable/vista"));
    }

    @Test
    void sinConsultaDeActivosNoSeListaLaTabla() throws Exception {
        Set<String> soloTransferencias = Set.of("opcion_ta", "opcion_trInterna", "opcion_trExterna");
        assertFalse(permite(soloTransferencias, "/administracion/activo/datatables"));
        assertTrue(permite(soloTransferencias, "/administracion/activo/transferencia-masiva"));
    }

    /** Caso cesar: solo Registro de Activos. El formulario da de alta oficina y responsable. */
    @Test
    void elFormularioDeActivosPuedeCrearOficinaYResponsable() throws Exception {
        Set<String> soloRegistro = Set.of("opcion_activo");
        assertTrue(permite(soloRegistro, "/administracion/oficina/registrar-oficina"));
        assertTrue(permite(soloRegistro, "/administracion/oficina/siguiente-codigo/7"));
        assertTrue(permite(soloRegistro, "/administracion/responsable/registrar-responsable"));
        assertTrue(permite(soloRegistro, "/administracion/correlativo/datos"));
        // pero no le abre los módulos completos de Oficinas ni Responsables
        assertFalse(permite(soloRegistro, "/administracion/oficina/vista"));
        assertFalse(permite(soloRegistro, "/administracion/responsable/vista"));
    }

    /** Caso yesica/saul/vero: Activos Pendientes usa toda la API bajo /activo/api. */
    @Test
    void activosPendientesLlegaASuPropiaApi() throws Exception {
        Set<String> pendientes = Set.of("opcion_activop");
        assertTrue(permite(pendientes, "/administracion/activo/api/aprobar/42"));
        assertTrue(permite(pendientes, "/administracion/activo/api/editar-lote"));
        assertTrue(permite(pendientes, "/administracion/activo/tabla-registros_pendientes"));
        assertTrue(permite(pendientes, "/administracion/responsable-entrega/api/listar"));
        assertTrue(permite(pendientes, "/administracion/correlativo/datos-por-codes"));
        assertFalse(permite(pendientes, "/administracion/activo/vista"));
    }

    /** El comodín vale por un segmento, no por cualquier cosa que venga después. */
    @Test
    void elComodinNoAbreRutasVecinas() throws Exception {
        Set<String> asignaciones = Set.of("opcion_aan");
        assertTrue(permite(asignaciones, "/administracion/activo/api/detalle/900"));
        assertFalse(permite(asignaciones, "/administracion/activo/api/aprobar-masivo"));
        assertFalse(permite(asignaciones, "/administracion/activo/vista"));
    }

    /** Faltantes resuelve hallazgos, que viven bajo el Mapa de Control. */
    @Test
    void faltantesPuedeResolverHallazgosSinElMapa() throws Exception {
        Set<String> faltantes = Set.of("opcion_control_faltantes");
        assertTrue(permite(faltantes, "/administracion/control-activos/hallazgos/12/resolver"));
        assertFalse(permite(faltantes, "/administracion/control-activos/vista"));
    }

    /**
     * APOYO con solo "Responsables": el alta abre el modal de oficina nueva, que pide el
     * correlativo y guarda bajo /administracion/oficina. Antes solo estaba habilitado el
     * formulario, y guardar la oficina daba 403.
     */
    @Test
    void responsablesPuedeCrearLaOficinaDesdeSuModal() throws Exception {
        Set<String> responsables = Set.of("opcion_responsable");
        assertTrue(permite(responsables, "/administracion/oficina/formulario"));
        assertTrue(permite(responsables, "/administracion/oficina/registrar-oficina"));
        assertTrue(permite(responsables, "/administracion/oficina/siguiente-codigo/7"));
        assertFalse(permite(responsables, "/administracion/oficina/eliminar/abc"));
        assertFalse(permite(responsables, "/administracion/oficina/vista"));
    }

    /** APOYO con solo "Oficinas": el alta de oficina busca por CI al responsable que registra junto. */
    @Test
    void oficinasPuedeBuscarAlResponsablePorCi() throws Exception {
        Set<String> oficinas = Set.of("opcion_oficina");
        assertTrue(permite(oficinas, "/administracion/responsable/api/personas/buscar-por-ci"));
        assertFalse(permite(oficinas, "/administracion/responsable/vista"));
        assertFalse(permite(oficinas, "/administracion/responsable/registrar-responsable"));
    }
}
