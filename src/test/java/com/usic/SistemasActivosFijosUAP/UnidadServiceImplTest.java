package com.usic.SistemasActivosFijosUAP;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.jdbc.core.JdbcTemplate;

import com.usic.SistemasActivosFijosUAP.model.ServiceImpl.UnidadServiceImpl;
import com.usic.SistemasActivosFijosUAP.model.dao.IUnidadDao;
import com.usic.SistemasActivosFijosUAP.model.entity.Unidad;

class UnidadServiceImplTest {
    @Test
    void alineaSecuenciaAntesDeRegistrarUnaUnidadNueva() {
        IUnidadDao dao = mock(IUnidadDao.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        Unidad unidad = new Unidad();
        when(dao.save(unidad)).thenReturn(unidad);

        assertSame(unidad, new UnidadServiceImpl(dao, jdbc).save(unidad));

        InOrder orden = inOrder(jdbc, dao);
        orden.verify(jdbc).execute("LOCK TABLE unidads IN SHARE ROW EXCLUSIVE MODE");
        orden.verify(jdbc).queryForObject(anyString(), eq(Long.class));
        orden.verify(dao).save(unidad);
    }

    @Test
    void noAjustaSecuenciaAlModificarUnidadExistente() {
        IUnidadDao dao = mock(IUnidadDao.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        Unidad unidad = new Unidad();
        unidad.setIdUnidad(4L);

        new UnidadServiceImpl(dao, jdbc).save(unidad);

        verify(jdbc, never()).execute(anyString());
        verify(jdbc, never()).queryForObject(anyString(), eq(Long.class));
        verify(dao).save(unidad);
    }
}
