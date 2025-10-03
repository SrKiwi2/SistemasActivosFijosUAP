package com.usic.SistemasActivosFijosUAP.config;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.google.api.client.util.Value;

@Configuration
public class DbfDataSourceConfig {
    @Value("${legacy.dbf.driverClassName}") String driver;
  @Value("${legacy.dbf.url}") String url;

  @Bean("dbfDataSource")
  public DataSource dbfDataSource() {
    var ds = new org.springframework.jdbc.datasource.DriverManagerDataSource();
    ds.setDriverClassName(driver);
    ds.setUrl(url);
    return ds;
  }

  @Bean("dbfJdbc")
  public org.springframework.jdbc.core.JdbcTemplate dbfJdbc(
      @Qualifier("dbfDataSource") DataSource ds) {
    return new org.springframework.jdbc.core.JdbcTemplate(ds);
  }
}
