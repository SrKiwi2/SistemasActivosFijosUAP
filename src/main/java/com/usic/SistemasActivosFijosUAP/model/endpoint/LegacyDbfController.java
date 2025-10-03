package com.usic.SistemasActivosFijosUAP.model.endpoint;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/legacy/dbf")
public class LegacyDbfController {

    private final JdbcTemplate dbfJdbc;

  public LegacyDbfController(@Qualifier("dbfJdbc") JdbcTemplate dbfJdbc) {
    this.dbfJdbc = dbfJdbc;
  }

  @GetMapping("/listar")
  public List<Map<String,Object>> listar(@RequestParam String tabla,
                                         @RequestParam(defaultValue = "50") int limit) {
    String sqlWithLimit = "SELECT * FROM " + tabla + " LIMIT " + limit;
    try {
      return dbfJdbc.queryForList(sqlWithLimit);
    } catch (Exception e) {
      return dbfJdbc.queryForList("SELECT * FROM " + tabla);
    }
  }

  @GetMapping("/columns")
  public List<String> columns(@RequestParam String tabla) {
    return dbfJdbc.query("SELECT * FROM " + tabla + " WHERE 1=0", rs -> {
      var md = rs.getMetaData();
      var cols = new java.util.ArrayList<String>();
      for (int i = 1; i <= md.getColumnCount(); i++) cols.add(md.getColumnName(i));
      return cols;
    });
  }

  @GetMapping("/buscar")
  public List<Map<String,Object>> buscar(@RequestParam String tabla,
                                         @RequestParam String columna,
                                         @RequestParam String q) {
    String sql = "SELECT * FROM " + tabla + " WHERE UPPER(" + columna + ") LIKE UPPER(?)";
    return dbfJdbc.queryForList(sql, "%" + q + "%");
  }

  // Endpoint de diagnóstico (ver qué datasource está usando)
  @GetMapping("/debug")
  public Map<String,Object> debug() throws Exception {
    var ds = dbfJdbc.getDataSource();
    try (var con = ds.getConnection()) {
      var md = con.getMetaData();
      return Map.of(
        "datasourceClass", ds.getClass().getName(),
        "jdbcUrl", md.getURL(),
        "driverName", md.getDriverName(),
        "userName", md.getUserName()
      );
    }
  }
}