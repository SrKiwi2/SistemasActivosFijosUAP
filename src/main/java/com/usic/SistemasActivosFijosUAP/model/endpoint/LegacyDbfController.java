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
  private final org.springframework.core.env.Environment env;

  public LegacyDbfController(
      @Qualifier("dbfJdbc") JdbcTemplate dbfJdbc,
      org.springframework.core.env.Environment env) {
    this.dbfJdbc = dbfJdbc;
    this.env = env;
  }

  // --- DEBUG que NO abre conexión ---
  @GetMapping("/debug-url")
  public Map<String,Object> debugUrl() {
    var ds = dbfJdbc.getDataSource();
    Map<String,Object> out = new java.util.LinkedHashMap<>();
    out.put("property_legacy_dbf_url", env.getProperty("legacy.dbf.url"));
    out.put("datasourceClass", ds != null ? ds.getClass().getName() : "null");
    return out;
  }

  // --- DEBUG que SÍ abre conexión (solo cuando ya esté montado) ---
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

  @GetMapping("/fs-check")
public Map<String,Object> fsCheck() {
  var p = java.nio.file.Path.of("/mnt/dbfwin");
  Map<String,Object> out = new java.util.LinkedHashMap<>();
  out.put("whoami", System.getProperty("user.name"));
  out.put("exists", java.nio.file.Files.exists(p));
  out.put("isDir", java.nio.file.Files.isDirectory(p));
  out.put("canRead", java.nio.file.Files.isReadable(p));
  try {
    var files = java.nio.file.Files.list(p)
      .map(x -> x.getFileName().toString())
      .sorted().limit(20).toList();
    out.put("list", files);
  } catch (Exception e) {
    out.put("listError", e.getClass().getSimpleName()+": "+e.getMessage());
  }
  return out;
}

  @GetMapping("/listar")
  public List<Map<String,Object>> listar(@RequestParam String tabla,
                                         @RequestParam(defaultValue = "50") int limit) {
    String table = "\"" + tabla + "\"";
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

  @GetMapping("/listar-javadbf")
public List<Map<String,Object>> listarJavaDbf(
    @RequestParam(defaultValue = "CODCONT") String tabla,
    @RequestParam(defaultValue = "50") int limit
) throws Exception {
  var path = java.nio.file.Path.of("/mnt/dbfwin", tabla.toUpperCase() + ".DBF");
  try (var in = java.nio.file.Files.newInputStream(path);
       var reader = new com.linuxense.javadbf.DBFReader(in)) {

    int fields = reader.getFieldCount();
    var headers = new java.util.ArrayList<String>(fields);
    for (int i = 0; i < fields; i++) {
      headers.add(reader.getField(i).getName());
    }

    var out = new java.util.ArrayList<Map<String,Object>>();
    Object[] row;
    int count = 0;
    while ((row = reader.nextRecord()) != null && count < limit) {
      var map = new java.util.LinkedHashMap<String,Object>();
      for (int i = 0; i < fields; i++) {
        map.put(headers.get(i), row[i]);
      }
      out.add(map);
      count++;
    }
    return out;
  }
}
}