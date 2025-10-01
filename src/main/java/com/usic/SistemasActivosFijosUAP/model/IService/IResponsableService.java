package com.usic.SistemasActivosFijosUAP.model.IService;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Service;

import com.usic.SistemasActivosFijosUAP.model.dao.IResposableDao;
import com.usic.SistemasActivosFijosUAP.model.dto.RespOption;
import com.usic.SistemasActivosFijosUAP.model.entity.Cargo;
import com.usic.SistemasActivosFijosUAP.model.entity.Oficina;
import com.usic.SistemasActivosFijosUAP.model.entity.Persona;
import com.usic.SistemasActivosFijosUAP.model.entity.Responsable;

@Service
public interface IResponsableService extends IServiceGenerico<Responsable, Long>{
    Responsable buscarPorCodigo(String codigo_funcionario);
    List<Responsable> listarResponsables();
    Responsable responsablePersonaOficinaCargo(Persona persona, Oficina oficina, Cargo cargo);
    List<Responsable> findAllByPersonaIdPersona(@Param("idPersona") Long idPersona);
    List<Responsable> findAllByPersona(Persona persona);
    Responsable buscarResponsablePorPersona(Persona persona);

    Optional<Responsable> findByOficinaAndCodigoFuncionario(Oficina oficina, String codigo_funcionario);
    Optional<Responsable> findByOficinaAndPersona(Oficina oficina, Persona persona);
    List<Responsable> saveAll(Iterable<Responsable> responsables);

    /* pra ver mejor lista de un repsonsbale */
    Page<IResposableDao.ResponsableRow> datatable(String q, Pageable pageable);
    long countActivos();

    Page<RespOption> search(@Param("term") String term, Pageable pageable);
}