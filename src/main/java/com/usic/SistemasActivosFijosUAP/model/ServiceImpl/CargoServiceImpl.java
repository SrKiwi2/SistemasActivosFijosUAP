package com.usic.SistemasActivosFijosUAP.model.ServiceImpl;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.usic.SistemasActivosFijosUAP.model.IService.ICargoService;
import com.usic.SistemasActivosFijosUAP.model.dao.ICargoDao;
import com.usic.SistemasActivosFijosUAP.model.entity.Cargo;

@Service
public class CargoServiceImpl implements ICargoService{

    @Autowired private ICargoDao dao;

    @Override
    public List<Cargo> findAll() {
        return dao.findAll();
    }

    @Override
    public Cargo findById(Long idEntidad) {
        return dao.findById(idEntidad).orElse(null);
    }

    @Override
    public Cargo save(Cargo entidad) {
        return dao.save(entidad);
    }

    @Override
    public void deleteById(Long idEntidad) {
        dao.deleteById(idEntidad);
    }

    /**
     * Cargo vigente con ese nombre exacto. Si hay más de uno, el más antiguo (antes lanzaba
     * error de resultado único). Si solo existe juntado en otro (FUSIONADO, Personas y cargos),
     * devuelve aquel en el que quedó: así Bajas, Transferencias, Asignaciones, Ingresos y el
     * alta de responsables no vuelven a crear un cargo ya juntado.
     */
    @Override
    public Cargo buscarPorNombre(String nombre) {
        List<Cargo> mismos = dao.findByNombreOrderByIdCargoAsc(nombre);
        for (Cargo c : mismos) if ("ACTIVO".equals(c.getEstado())) return c;
        for (Cargo c : mismos) {
            Cargo d = c;
            for (int saltos = 0; d != null && "FUSIONADO".equals(d.getEstado()) && d.getFusionadoEn() != null && saltos < 10; saltos++) {
                d = dao.findById(d.getFusionadoEn()).orElse(null);
            }
            if (d != null && "ACTIVO".equals(d.getEstado())) return d;
        }
        return null;
    }

    @Override
    public List<Cargo> listarCargos() {
        return dao.listarCargos();
    }

    @Override
    public Optional<Cargo> findFirstByNombreIgnoreCase(String nombre) {
        return dao.findFirstByNombreIgnoreCase(nombre);
    }

    @Override
    public Cargo buscarOCrearPorNombre(String nombre, Long idUsuarioRegistro) {
        if (nombre == null || nombre.trim().isEmpty()) {
            return null;
        }
        
        // 1. Buscar por nombre ignorando mayúsculas
        Optional<Cargo> cargoExistente = dao.findByNombreIgnoreCase(nombre.trim());
        
        if (cargoExistente.isPresent()) {
            return cargoExistente.get(); // Retorna el existente
        }
        
        // 2. Si no existe, crear uno nuevo
        Cargo nuevoCargo = new Cargo();
        nuevoCargo.setNombre(nombre.trim().toUpperCase()); // O solo trim() si prefieres minúsculas
        nuevoCargo.setDescripcion("Registrado automáticamente desde API.");
        // Establecer valores de AuditoriaConfig
        nuevoCargo.setEstado("ACTIVO");
        if (idUsuarioRegistro != null) {
            nuevoCargo.setRegistroIdUsuario(idUsuarioRegistro);
        }

        return dao.save(nuevoCargo); // Guarda y retorna el nuevo Cargo
    }

    @Override
    public List<Cargo> buscarPorNombreLike(String nombre) {
       return dao.buscarPorNombreLike(nombre);
    }

    @Override
    public List<String> nombresParecidos(String texto, int max) {
        String t = texto == null ? "" : texto.trim().toUpperCase();
        return dao.nombresParecidos("%" + t + "%", org.springframework.data.domain.PageRequest.of(0, Math.max(1, max)));
    }
}