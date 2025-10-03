package com.usic.SistemasActivosFijosUAP.model.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.usic.SistemasActivosFijosUAP.model.dto.RespOption;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class FuncionesResponsableRepo {

    private final JdbcTemplate jdbc;

    // para cargar responsables
    private static final RowMapper<RespOption> MAPPER = new RowMapper<RespOption>() {
        @Override public RespOption mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new RespOption(rs.getLong("id"), rs.getString("text"));
        }
    };

    public Page<RespOption> search(String term, Pageable pageable) {
        String q = (term == null) ? "" : term.trim();
        int limit  = pageable.getPageSize();
        int offset = (int) pageable.getOffset();

        List<RespOption> data = jdbc.query(
            "select * from search_responsables(?, ?, ?)",
            MAPPER,
            q, limit, offset
        );

        Long total = jdbc.queryForObject(
            "select count_search_responsables(?)",
            Long.class,
            q
        );

        return new PageImpl<>(data, pageable, total == null ? 0L : total);
    }

    public String siguienteCodigoPorOficinaStr(Long idOficina) {
        return jdbc.queryForObject(
                "select generar_codigo_funcionario_by_oficina(?)::text",
                String.class,
                idOficina
        );
    }
}
