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
    String table = "\"" + tabla + "\""; // por si el driver requiere comillas
    String sqlWithLimit = "SELECT * FROM " + table + " LIMIT " + limit;
    try {
      return dbfJdbc.queryForList(sqlWithLimit);
    } catch (Exception e) {
      return dbfJdbc.queryForList("SELECT * FROM " + table);
    }
  }

  @GetMapping("/columns")
  public List<String> columns(@RequestParam String tabla) {
    String table = "\"" + tabla + "\"";
    return dbfJdbc.query("SELECT * FROM " + table + " WHERE 1=0", rs -> {
      var md = rs.getMetaData();
      var cols = new java.util.ArrayList<String>();
      for (int i = 1; i <= md.getColumnCount(); i++) cols.add(md.getColumnName(i));
      return cols;
    });
  }

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