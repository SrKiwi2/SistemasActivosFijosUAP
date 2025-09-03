package com.usic.SistemasActivosFijosUAP.model.ServiceImpl;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.linuxense.javadbf.DBFReader;
import com.usic.SistemasActivosFijosUAP.model.IService.IGrupoContableService;
import com.usic.SistemasActivosFijosUAP.model.dao.IGrupoContableDao;
import com.usic.SistemasActivosFijosUAP.model.entity.GrupoContable;

@Service
public class GrupoContableServiceImpl implements IGrupoContableService{

    @Autowired private IGrupoContableDao dao;

    @Override
    public List<GrupoContable> findAll() {
        return dao.findAll();
    }

    @Override
    public GrupoContable findById(Long idEntidad) {
        return dao.findById(idEntidad).orElse(null);
    }

    @Override
    public GrupoContable save(GrupoContable entidad) {
        return dao.save(entidad);
    }

    @Override
    public void deleteById(Long idEntidad) {
        dao.deleteById(idEntidad);
    }

    @Override
    public GrupoContable buscarPorNombre(String nombre) {
        return dao.buscarPorNombre(nombre);
    }

    @Override
    public List<GrupoContable> listarGruposContables() {
        return dao.listarGruposContables();
    }

    @Override
    public GrupoContable buscarPorCodigo(Integer codigo) {
        return dao.buscarPorCodigo(codigo);
    }

    @Override
    public void importarDesdeDBF(File archivoDBF) {
        try (InputStream inputStream = new FileInputStream(archivoDBF);
             DBFReader reader = new DBFReader(inputStream)) {

            Object[] rowObjects;

            while ((rowObjects = reader.nextRecord()) != null) {
                GrupoContable grupo = new GrupoContable();

                grupo.setCodContable(rowObjects[0] != null ? ((BigDecimal) rowObjects[0]).intValue() : null);
                grupo.setNombre(rowObjects[1] != null ? ((String) rowObjects[1]).trim() : null);
                grupo.setVidaUtil(rowObjects[2] != null ? ((BigDecimal) rowObjects[2]).intValue() : null);
                grupo.setDepreciar(rowObjects[4] != null && (Boolean) rowObjects[4]);
                grupo.setActualizar(rowObjects[5] != null && (Boolean) rowObjects[5]);
                grupo.setEstado("ACTIVO");

                if (grupo.getNombre() != null && buscarPorNombre(grupo.getNombre()) == null) {
                    dao.save(grupo);
                }
            }


        } catch (IOException e) {
            throw new RuntimeException("Error al leer el archivo DBF", e);
        }
    }

    @Override
    public List<GrupoContable> saveAll(Iterable<GrupoContable> grupoContables) {
        return dao.saveAll(grupoContables);
    }

    @Override
    public Optional<GrupoContable> findByCodContable(Integer codContable) {
        return dao.findByCodContable(codContable);
    }

    @Override
    public Optional<GrupoContable> findFirstByNombreIgnoreCase(String nombre) {
        return dao.findFirstByNombreIgnoreCase(nombre);
    }
}