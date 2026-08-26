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
import com.usic.SistemasActivosFijosUAP.model.entity.GrupoContable;
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
    @Transactional(readOnly = true)
    public boolean isNombreUnique(String nombre, Long idPredio, Long idGrupoContable, Long idAuxiliar) {
        if (nombre == null || nombre.isBlank()) return true;
        // Sin predio y grupo no hay ámbito contra el cual comparar. Antes se comparaba
        // contra TODA la tabla y eso impedía dar de alta en un predio un nombre que ya
        // existía en otro — que es exactamente lo que rompía el registro de auxiliares.
        if (idPredio == null || idGrupoContable == null) return true;

        String n = nombre.trim();
        return (idAuxiliar == null)
            ? !dao.existsByPredio_IdPredioAndGrupoContable_IdGrupoContableAndNombreIgnoreCase(
                    idPredio, idGrupoContable, n)
            : !dao.existsByPredio_IdPredioAndGrupoContable_IdGrupoContableAndNombreIgnoreCaseAndIdAuxiliarNot(
                    idPredio, idGrupoContable, n, idAuxiliar);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Auxiliar> findByPredioIdPredioAndGrupoContableIdGrupoContable(Long idPredio, Long idGrupoContable) {
        return dao.findByPredioIdPredioAndGrupoContableIdGrupoContable(idPredio, idGrupoContable);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Auxiliar> findVigentesByPredioYGrupo(Long idPredio, Long idGrupoContable) {
        if (idPredio == null || idGrupoContable == null) return List.of();
        return dao.findVigentesByPredioYGrupo(idPredio, idGrupoContable);
    }

    @Override
    public Integer findMaxCodAux(Long idPredio, Long idGrupo) {
        return dao.findMaxCodAux(idPredio, idGrupo);
    }

    @Override
    public Optional<Auxiliar> findByPredioIdPredioAndGrupoContableIdGrupoContableAndNombreIgnoreCase(Long idPredio,
            Long idGrupo, String nombre) {
        return dao.findByPredioIdPredioAndGrupoContableIdGrupoContableAndNombreIgnoreCase(idPredio, idGrupo, nombre);
    }

    @Override
    public Optional<Auxiliar> findByGrupoContableAndCodAux(GrupoContable grupe, Short codAux) {
        return dao.findByGrupoContableAndCodAux(grupe, codAux);
    }
}
