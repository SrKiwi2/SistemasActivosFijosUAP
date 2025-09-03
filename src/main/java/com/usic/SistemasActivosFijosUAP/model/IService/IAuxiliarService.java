package com.usic.SistemasActivosFijosUAP.model.IService;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.usic.SistemasActivosFijosUAP.model.entity.Auxiliar;
import com.usic.SistemasActivosFijosUAP.model.entity.GrupoContable;
import com.usic.SistemasActivosFijosUAP.model.entity.Predio;

@Service
public interface IAuxiliarService extends IServiceGenerico<Auxiliar, Long>{
    List<Auxiliar> saveAll(List<Auxiliar> list);
    Optional<Auxiliar> findByPredioAndCodAux(Predio predio, Short codAux);
    Optional<Auxiliar> findByPredioAndGrupoContableAndCodAux(Predio predio, GrupoContable gc, Short codAux);
    Optional<Auxiliar> findFirstByPredioAndNombreIgnoreCase(Predio predio, String nombre);
}
