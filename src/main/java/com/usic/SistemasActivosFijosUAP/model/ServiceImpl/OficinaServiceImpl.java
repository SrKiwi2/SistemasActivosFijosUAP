package com.usic.SistemasActivosFijosUAP.model.ServiceImpl;

import java.util.Date;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.usic.SistemasActivosFijosUAP.model.IService.IOficinaService;
import com.usic.SistemasActivosFijosUAP.model.dao.IOficinaDao;
import com.usic.SistemasActivosFijosUAP.model.entity.Entidad;
import com.usic.SistemasActivosFijosUAP.model.entity.Oficina;
import com.usic.SistemasActivosFijosUAP.model.entity.Predio;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;

@Service
public class OficinaServiceImpl implements IOficinaService{

    @Autowired private IOficinaDao dao;
    @Autowired private EntityManager em;

    @Override
    public List<Oficina> findAll() {
        return dao.findAll();
    }

    @Override
    public Oficina findById(Long idEntidad) {
        return dao.findById(idEntidad).orElse(null);
    }

    @Override
    public Oficina save(Oficina entidad) {
        return dao.save(entidad);
    }

    @Override
    public void deleteById(Long idEntidad) {
        dao.deleteById(idEntidad);
    }

    @Override
    public List<Oficina> listarOficinas() {
        return dao.listarOficinas();
    }
    
    @Override
    public List<Oficina> buscarPorNombreParcial(String termino) {
        return dao.buscarPorNombreParcial(termino);
    }

    @Override
    public Optional<Oficina> findByPredioAndCodOfi(Predio predio, Short codOfi) {
        return dao.findByPredioAndCodOfi(predio, codOfi);
    }

    @Override
    public List<Oficina> saveAll(Iterable<Oficina> oficinas) {
        return dao.saveAll(oficinas);
    }

    @Override
    public Optional<Oficina> buscarPorNombre(String nombre) {
        return dao.buscarPorNombre(nombre);
    }

    @Override
    public List<Oficina> buscarPorCodigo(Short codOfi) {
        return dao.buscarPorCodigo(codOfi);
    }

    @Override
    @Transactional
    public short nextCodOfiForPredio(Long idPredio) {
        // Bloquea el predio para que solo un hilo a la vez asigne correlativo
        Predio locked = em.find(Predio.class, idPredio, LockModeType.PESSIMISTIC_WRITE);
        if (locked == null) {
            throw new IllegalArgumentException("Predio no existe: " + idPredio);
        }
        Short max = dao.maxCodOfiPorPredio(idPredio); // puede ser null (coalesce ya lo evita)
        short next = (short) ((max == null ? 0 : max) + 1);
        if (next <= 0) { // protección de overflow de short (32767)
            throw new IllegalStateException("Se alcanzó el máximo correlativo para este predio");
        }
        return next;
    }

    @Override
    @Transactional
    public Oficina findOrCreateConCorrelativo(String nombreOficina, Predio predio, Long idUsuario) {
        return dao.findByNombre(nombreOficina)
            .orElseGet(() -> {
                short cod = nextCodOfiForPredio(predio.getIdPredio());
                Oficina o = new Oficina();
                o.setNombre(nombreOficina.trim());
                o.setEstado("API");
                o.setRegistro(new Date());
                o.setRegistroIdUsuario(idUsuario);
                o.setCodOfi(cod);
                o.setPredio(predio);
                return dao.save(o);
            });
    }

    @Override
    public Optional<Oficina> findByUnidadAndCodOfi(String unidad, Short codOfi) {
        return dao.findByUnidadAndCodOfi(unidad, codOfi);
    }

    @Override
    public List<Oficina> buscarPorQ(String q) {
                if (q==null || q.isBlank()) return dao.listarOficinas();
        return dao.buscarPorQ(q.trim());
    }

    @Override
    public Optional<Oficina> findByEntidadUnidadAndCodOfi(Entidad entidad, String unidad, Short codOfi) {
        return dao.findByEntidadUnidadAndCodOfi(entidad, unidad, codOfi);
    }

    @Override
    public Short findNextCodOfiByPredioId(Long idPredio) {
        return dao.findNextCodOfiByPredioId(idPredio);
    }

    @Override
    public List<Oficina> findByPredioIdPredio(Long idPredio) {
        return dao.findByPredioIdPredio(idPredio);
    }

    @Override
    public Optional<Oficina> findByCodOfiAndPredio(Short codOfi, Predio predio) {
        return dao.findByCodOfiAndPredio(codOfi, predio);
    }
}