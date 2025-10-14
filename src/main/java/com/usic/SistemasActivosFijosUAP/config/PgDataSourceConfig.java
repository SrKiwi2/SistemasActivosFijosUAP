package com.usic.SistemasActivosFijosUAP.config;

import javax.sql.DataSource;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class PgDataSourceConfig {
    // DataSource de Postgres ya lo crea Spring Boot desde spring.datasource.*,
  // pero lo marcamos explícitamente como @Primary por claridad.

  @Primary
  @Bean(name = "pgJdbcTemplate")
  public JdbcTemplate pgJdbcTemplate(DataSource dataSource) {
    // Este DataSource es el principal (spring.datasource.*)
    return new JdbcTemplate(dataSource);
  }
}
