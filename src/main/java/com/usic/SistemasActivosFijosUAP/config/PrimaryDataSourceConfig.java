package com.usic.SistemasActivosFijosUAP.config;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

@Configuration
public class PrimaryDataSourceConfig {
@Bean
  @Primary
  public DataSource primaryDataSource(
      @Value("${spring.datasource.url}") String url,
      @Value("${spring.datasource.username}") String username,
      @Value("${spring.datasource.password}") String password,
      @Value("${spring.datasource.driverClassName}") String driver) {

    HikariConfig cfg = new HikariConfig();
    cfg.setJdbcUrl(url);              // <- forzamos jdbcUrl explícitamente
    cfg.setUsername(username);
    cfg.setPassword(password);
    cfg.setDriverClassName(driver);
    // (opcional) cfg.setMaximumPoolSize(10); etc.

    return new HikariDataSource(cfg);
  }
}
