package com.usic.SistemasActivosFijosUAP.model.endpoint;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/legacy/dbf")
@RequiredArgsConstructor
public class LegacyDbfController {
    @Qualifier("dbfJdbc")
  private final JdbcTemplate dbfJdbc;

  // A) Listar primeras filas (genérico)
  @GetMapping("/listar")
  public List<Map<String,Object>> listar(@RequestParam String tabla,
                                         @RequestParam(defaultValue = "50") int limit) {
    // Algunos drivers no soportan LIMIT. Probamos con y sin:
    String sqlWithLimit = "SELECT * FROM " + tabla + " LIMIT " + limit;
    try {
      return dbfJdbc.queryForList(sqlWithLimit);
    } catch (Exception e) {
      // fallback sin LIMIT
      return dbfJdbc.queryForList("SELECT * FROM " + tabla);
    }
  }

  // B) Ver columnas de una tabla (útil para armar DTOs)
  @GetMapping("/columns")
  public List<String> columns(@RequestParam String tabla) {
    return dbfJdbc.query("SELECT * FROM " + tabla + " WHERE 1=0", rs -> {
      var md = rs.getMetaData();
      var cols = new java.util.ArrayList<String>();
      for (int i = 1; i <= md.getColumnCount(); i++) cols.add(md.getColumnName(i));
      return cols;
    });
  }

  // C) Filtro simple por una columna (ajústala a tus nombres reales)
  @GetMapping("/buscar")
  public List<Map<String,Object>> buscar(@RequestParam String tabla,
                                         @RequestParam String columna,
                                         @RequestParam String q) {
    // Muchos drivers no soportan parámetros sobre nombres de columna, pero sí sobre valores:
    String sql = "SELECT * FROM " + tabla + " WHERE UPPER(" + columna + ") LIKE UPPER(?)";
    return dbfJdbc.queryForList(sql, "%" + q + "%");
  }

//  mvn install:install-file \
//  -Dfile=/ruta/al/hxtt-dbf.jar \
//  -DgroupId=com.hxtt \
//  -DartifactId=hxtt-dbf \
//  -Dversion=1.0 \
//  -Dpackaging=jar
//
}
