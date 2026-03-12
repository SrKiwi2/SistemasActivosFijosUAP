package com.usic.SistemasActivosFijosUAP.model.ServiceImpl;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.usic.SistemasActivosFijosUAP.model.IService.IAuxiliarService;
import com.usic.SistemasActivosFijosUAP.model.dao.IAuxiliarDao;
import com.usic.SistemasActivosFijosUAP.model.dto.AuxOption;
import com.usic.SistemasActivosFijosUAP.model.entity.Auxiliar;
import com.usic.SistemasActivosFijosUAP.model.entity.Predio;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuxiliarServiceImpl implements IAuxiliarService {

    private final IAuxiliarDao dao;

    @Override
    public List<Auxiliar> findAll() {
        return dao.findAll();
    }

    @Override
    public Auxiliar findById(Long idEntidad) {
        return dao.findById(idEntidad).orElse(null);
    }

    @Override
    public Auxiliar save(Auxiliar entidad) {
        return dao.save(entidad);
    }

    @Override
    public void deleteById(Long idEntidad) {
        dao.deleteById(idEntidad);
    }

    @Override
    public Optional<Auxiliar> findByPredioAndCodAux(Predio predio, Short codAux) {
        return dao.findByPredioAndCodAux(predio, codAux);
    }

    @Override
    public Optional<Auxiliar> findFirstByPredioAndNombreIgnoreCase(Predio predio, String nombre) {
        return dao.findFirstByPredioAndNombreIgnoreCase(predio, nombre);
    }

    @Override
    public List<Auxiliar> saveAll(List<Auxiliar> list) {
        return dao.saveAll(list);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AuxOption> searchByGrupo(Long grupoId, Long predioId, String term, Pageable pageable) {
        return dao.searchByGrupo(
                grupoId,
                predioId,
                term == null ? "" : term.trim(),
                pageable);
    }

    @Override
    public List<Auxiliar> buscarPorQ(String q) {
        return (q == null || q.isBlank()) ? dao.listarTodo() : dao.buscarPorQ(q.trim());
    }

    @Override
    public List<Auxiliar> listarTodo() {
        return dao.listarTodo();
    }

    @Transactional(readOnly = true)
    public Optional<Auxiliar> findByPredio_IdPredioAndGrupoContable_IdGrupoContableAndCodAux(
            Long predioId, Long grupoId, Short codAux) {
        return dao.findByPredio_IdPredioAndGrupoContable_IdGrupoContableAndCodAux(predioId, grupoId, codAux);
    }

    @Override
    public Short getNextCodAux(Long idPredio, Long idGrupoContable) {
        return dao.findNextCodAux(idPredio, idGrupoContable);
    }

    @Override
    public boolean isNombreUnique(String nombre, Long idAuxiliar) {
        if (idAuxiliar == null) {
        // Modo Registro: Solo verificamos si el nombre ya existe
        return !dao.existsByNombreIgnoreCase(nombre);
    } else {
        // Modo Edición: Verificamos si existe otro (diferente al actual) con ese nombre
        return !dao.existsByNombreIgnoreCaseAndIdAuxiliarIsNot(nombre, idAuxiliar);
    }
    }

    @Override
    @Transactional(readOnly = true)
    public List<Auxiliar> findByPredioIdPredioAndGrupoContableIdGrupoContable(Long idPredio, Long idGrupoContable) {
        return dao.findByPredioIdPredioAndGrupoContableIdGrupoContable(idPredio, idGrupoContable);
    }
}
