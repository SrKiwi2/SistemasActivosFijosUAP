package com.usic.SistemasActivosFijosUAP;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.usic.SistemasActivosFijosUAP.model.ServiceImpl.OpcionMenuServiceImpl;
import com.usic.SistemasActivosFijosUAP.model.dao.IOpcionMenuDao;
import com.usic.SistemasActivosFijosUAP.model.entity.OpcionMenu;
import com.usic.SistemasActivosFijosUAP.model.entity.Rol;
import com.usic.SistemasActivosFijosUAP.model.entity.Usuario;

class PermisosAdministradorAccionesTest {

    @Test
    void lasAccionesSiguenLasCasillasPropiasAunqueElMenuSeaCompleto() {
        AtomicReference<List<String>> propios = new AtomicReference<>(List.of());
        List<OpcionMenu> items = List.of(
                item("opcion_activo", "/administracion/activo/formulario"),
                item("opcion_activo_desaprobar", null),
                item("opcion_activop_subir_vsiaf", null));
        IOpcionMenuDao dao = (IOpcionMenuDao) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] { IOpcionMenuDao.class }, (proxy, method, args) -> switch (method.getName()) {
                    case "findByTipoOrderByOrdenAsc" -> items;
                    case "findCodigosByUsuario" -> propios.get();
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        OpcionMenuServiceImpl servicio = new OpcionMenuServiceImpl();
        ReflectionTestUtils.setField(servicio, "opcionMenuDao", dao);
        Usuario admin = new Usuario();
        admin.setIdUsuario(1L);
        Rol rol = new Rol();
        rol.setNombre("ADMINISTRADOR");
        admin.setRol(rol);

        Set<String> plantilla = servicio.opcionesEfectivas(admin);
        assertTrue(plantilla.contains("opcion_activo_desaprobar"));
        assertFalse(plantilla.contains("opcion_activop_subir_vsiaf"));

        propios.set(List.of("opcion_activo"));
        Set<String> quitado = servicio.opcionesEfectivas(admin);
        assertTrue(quitado.contains("opcion_activo"));
        assertFalse(quitado.contains("opcion_activo_desaprobar"));

        propios.set(List.of("opcion_activo", "opcion_activo_desaprobar", "opcion_activop_subir_vsiaf"));
        Set<String> agregado = servicio.opcionesEfectivas(admin);
        assertTrue(agregado.contains("opcion_activo_desaprobar"));
        assertTrue(agregado.contains("opcion_activop_subir_vsiaf"));
    }

    private OpcionMenu item(String codigo, String url) {
        OpcionMenu opcion = new OpcionMenu();
        opcion.setCodigo(codigo);
        opcion.setUrl(url);
        opcion.setTipo("ITEM");
        opcion.setEstado(OpcionMenu.ESTADO_ACTIVO);
        return opcion;
    }
}
