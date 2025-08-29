package com.usic.SistemasActivosFijosUAP.model.dao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.usic.SistemasActivosFijosUAP.model.entity.Ingreso;

public interface IIngresoDao extends JpaRepository <Ingreso, Long>{
    
    @Query("""
        select distinct i
        from Ingreso i
        left join fetch i.responsablePropietario rp
        left join fetch rp.persona p
        left join fetch i.oficinaPropietario op
        left join fetch i.detalles d
        order by i.idIngreso desc
    """)
    List<Ingreso> findAllWithTodo();
}
