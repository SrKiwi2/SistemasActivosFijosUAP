// package com.usic.SistemasActivosFijosUAP.config;

// import java.io.File;

// import javax.sql.DataSource;

// import org.slf4j.Logger;
// import org.slf4j.LoggerFactory;
// import org.springframework.beans.factory.annotation.Autowired;
// import org.springframework.beans.factory.annotation.Qualifier;
// import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
// import org.springframework.context.annotation.Bean;
// import org.springframework.context.annotation.Configuration;
// import org.springframework.context.annotation.Lazy;
// import org.springframework.core.env.Environment;
// import org.springframework.jdbc.core.JdbcTemplate;
// import org.springframework.jdbc.datasource.SimpleDriverDataSource;

// import com.hxtt.sql.dbf.DBFDriver;

// @Configuration(proxyBeanMethods = false)
// public class DbfDataSourceConfig {

//     private static final Logger log = LoggerFactory.getLogger(DbfDataSourceConfig.class);

//     @Autowired
//     private Environment env;


//     @Bean(name = "dbfDataSource")
//     @Lazy
//     @ConditionalOnExpression("T(org.springframework.util.StringUtils).hasText('${legacy.dbf.url:}')")
//     public DataSource dbfDataSource() {
//         String url = env.getProperty("legacy.dbf.url");
//         String driverClass = env.getProperty("legacy.dbf.driverClassName", "com.hxtt.sql.dbf.DBFDriver");

//         log.info("[DBF] Preparando DataSource");
//         log.info("[DBF] URL: {}", url);
//         log.info("[DBF] Driver: {}", driverClass);

//         // Verificar que el directorio existe antes de crear el DataSource
//         String path = url.replace("jdbc:dbf:", "").split("\\?")[0];
//         File dbfDir = new File(path);
        
//         if (!dbfDir.exists()) {
//             throw new IllegalStateException(
//                 String.format("El directorio DBF no existe: %s. Verifique el montaje CIFS.", path)
//             );
//         }
        
//         if (!dbfDir.canRead()) {
//             throw new IllegalStateException(
//                 String.format("El directorio DBF no es legible: %s. Verifique permisos.", path)
//             );
//         }
        
//         log.info("[DBF] Directorio verificado: {} (existe y es legible)", path);

//         try {
//             Class.forName(driverClass);
//             log.info("[DBF] Driver cargado exitosamente");
//         } catch (ClassNotFoundException e) {
//             throw new IllegalStateException("No se encontró el driver HXTT: " + driverClass, e);
//         }

//         try {
//             DataSource ds = new SimpleDriverDataSource(new DBFDriver(), url, null, null);
//             log.info("[DBF] DataSource creado exitosamente");
//             return ds;
//         } catch (Exception e) {
//             log.error("[DBF] Error creando DataSource: {}", e.getMessage(), e);
//             throw new IllegalStateException("Error al crear DataSource DBF", e);
//         }
//     }

//     @Bean(name = "dbfJdbc")
//     @Lazy
//     @ConditionalOnExpression("T(org.springframework.util.StringUtils).hasText('${legacy.dbf.url:}')")
//     public JdbcTemplate dbfJdbc(@Qualifier("dbfDataSource") DataSource ds) {
//         log.info("[DBF] Creando JdbcTemplate");
//         return new JdbcTemplate(ds);
//     }
// }