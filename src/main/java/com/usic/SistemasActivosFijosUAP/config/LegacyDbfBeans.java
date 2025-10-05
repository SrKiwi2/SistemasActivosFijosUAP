package com.usic.SistemasActivosFijosUAP.config;

import java.nio.file.Path;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.usic.SistemasActivosFijosUAP.interoperabilidad.JavaDbfService;

@Configuration
public class LegacyDbfBeans {
  
  @Bean
  public JavaDbfService javaDbfService() {
    return new JavaDbfService(Path.of("/mnt/dbfwin"), "CP1252");
  }
}
