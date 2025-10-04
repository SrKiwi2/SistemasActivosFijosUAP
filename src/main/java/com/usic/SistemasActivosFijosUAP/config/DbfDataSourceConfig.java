package com.usic.SistemasActivosFijosUAP.config;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;

import com.hxtt.sql.dbf.DBFDriver;

@Configuration(proxyBeanMethods = false)
public class DbfDataSourceConfig {

    @Autowired
    private Environment env;

    /**
     * Crea el DataSource SOLO si legacy.dbf.url tiene texto (ni null, ni vacío).
     * Usamos SimpleDriverDataSource con el driver inyectado explícitamente para
     * evitar el "driverClassName must not be empty".
     */
    @Bean(name = "dbfDataSource")
    @Lazy
    @ConditionalOnExpression("T(org.springframework.util.StringUtils).hasText('${legacy.dbf.url:}')")
    public DataSource dbfDataSource() {
        String url = env.getProperty("legacy.dbf.url");
        String driverClass = env.getProperty("legacy.dbf.driverClassName", "com.hxtt.sql.dbf.DBFDriver");

        System.out.println("[DBF] Preparando DataSource: url=" + url + " | driver=" + driverClass);

        try {
            // Aseguramos que el driver está en el classpath
            Class.forName(driverClass);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("No se encontró el driver HXTT: " + driverClass, e);
        }

        // Creamos el DS con el driver real (sin user/pass)
        return new SimpleDriverDataSource(new DBFDriver(), url, null, null);
    }

    @Bean(name = "dbfJdbc")
    @Lazy
    @ConditionalOnExpression("T(org.springframework.util.StringUtils).hasText('${legacy.dbf.url:}')")
    public JdbcTemplate dbfJdbc(@Qualifier("dbfDataSource") DataSource ds) {
        return new JdbcTemplate(ds);
    }
}