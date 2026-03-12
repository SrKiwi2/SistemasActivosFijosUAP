package com.usic.SistemasActivosFijosUAP.model.IService;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Service;

import com.usic.SistemasActivosFijosUAP.model.dao.IResposableDao;
import com.usic.SistemasActivosFijosUAP.model.dto.RespOption;
import com.usic.SistemasActivosFijosUAP.model.dto.responsable.ResponsableApiDataDTO;
import com.usic.SistemasActivosFijosUAP.model.entity.Cargo;
import com.usic.SistemasActivosFijosUAP.model.entity.Oficina;
import com.usic.SistemasActivosFijosUAP.model.entity.Persona;
import com.usic.SistemasActivosFijosUAP.model.entity.Responsable;

@Service
public interface IResponsableService extends IServiceGenerico<Responsable, Long>{
    Responsable buscarPorCodigo(String codigoApi);
    List<Responsable> listarResponsables();
    Responsable responsablePersonaOficinaCargo(Persona persona, Oficina oficina, Cargo cargo);
    List<Responsable> findAllByPersonaIdPersona(@Param("idPersona") Long idPersona);
    List<Responsable> findAllByPersona(Persona persona);
    List<Responsable> findByPersonaAndEstado(Persona persona, String estado);
    Optional<Responsable> findByOficinaAndCodigoFuncionario(Oficina oficina, String codigo_funcionario);
    Optional<Responsable> findByOficinaAndPersona(Oficina oficina, Persona persona);
    List<Responsable> saveAll(Iterable<Responsable> responsables);
    Page<IResposableDao.ResponsableRow> datatable(String q, Long oficinaId, Pageable pageable);
    long countActivos();
    Page<RespOption> search(@Param("term") String term, Pageable pageable);
    ResponsableApiDataDTO getResponsableDataFromApi(String codigoFuncionario, String ci);

    //*MODULO REGISTRO RESPOSNABLE */
    Responsable findByCodigoFuncionario(String codigoFuncionario);
    Responsable findByCodigoFuncionarioYOficina(String codigoFuncionario, Long idOficina);
    boolean existeResponsablePorPersona(Long idPersona);
    boolean existeResponsablePorPersonaYOficina(Long idPersona, Long idOficina);
    List<Responsable> findByPersonaId(Long idPersona);

    Responsable findByIdWithRelations(Long id);

    Page<RespOption> searchByOficina(@Param("oficinaId") Long oficinaId, @Param("q") String q, Pageable pageable);

    List<Responsable> findByOficinaIdOficina(Long idOficina);

    Page<RespOption> searchGlobal(@Param("q") String q, Pageable pageable);

    boolean existsByOficinaIdOficinaAndPersonaIdPersona(Long idOficina, Long idPersona);
    
}