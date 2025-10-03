package com.usic.SistemasActivosFijosUAP.config;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import com.google.api.client.util.Value;

@Configuration
public class DbfDataSourceConfig {
    // Fallback a la clase de HXTT si no llega la property
  @Value("${legacy.dbf.driverClassName:com.hxtt.sql.dbf.DBFDriver}")
  private String driver;

  // Si no llega la property, forzamos un valor inválido para que se note en el log
  @Value("${legacy.dbf.url:#{null}}")
  private String url;

  @Bean(name = "dbfDataSource")
  public DataSource dbfDataSource() {
    if (url == null || url.isBlank()) {
      throw new IllegalArgumentException(
        "legacy.dbf.url no está configurado. Ej: jdbc:dbf:/mnt/dbfwin?charSet=CP1252");
    }
    DriverManagerDataSource ds = new DriverManagerDataSource();
    ds.setDriverClassName(driver);
    ds.setUrl(url);
    System.out.println("[DBF] driver=" + driver + " | url=" + url);
    return ds;
  }

  @Bean(name = "dbfJdbc")
  public JdbcTemplate dbfJdbc(@Qualifier("dbfDataSource") DataSource ds) {
    return new JdbcTemplate(ds);
  }
}
