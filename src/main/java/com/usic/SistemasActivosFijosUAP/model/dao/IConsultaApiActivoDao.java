package com.usic.SistemasActivosFijosUAP.model.dao;

import org.springframework.data.jpa.repository.JpaRepository;

import com.usic.SistemasActivosFijosUAP.model.entity.ConsultaApiActivo;

public interface IConsultaApiActivoDao extends JpaRepository<ConsultaApiActivo, Long> {
}
