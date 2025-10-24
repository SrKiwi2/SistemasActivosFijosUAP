package com.usic.SistemasActivosFijosUAP.interoperabilidad.registroDbf;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.usic.SistemasActivosFijosUAP.model.entity.Activo;

@Service
public class ActualDbfWriterService {
    
    private final JdbcTemplate dbfJdbc;
    private final Object actualLock = new Object(); // para evitar colisiones concurrentes

    public ActualDbfWriterService(@Qualifier("dbfJdbc") JdbcTemplate dbfJdbc) {
        this.dbfJdbc = dbfJdbc;
    }

    // Opcional: por si necesitas convertir nulos a tipos JDBC
    private static void setParam(PreparedStatement ps, int idx, Object val, int sqlType) throws SQLException {
        if (val == null)
            ps.setNull(idx, sqlType);
        else
            ps.setObject(idx, val, sqlType);
    }

    /**
     * Verifica si ya existe un registro por CODIGO en ACTUAL.DBF (evita
     * duplicados).
     */
    public boolean existsByCodigo(String codigo) {
        String sql = "SELECT COUNT(*) FROM ACTUAL WHERE CODIGO = ?";
        Integer n = dbfJdbc.queryForObject(sql, Integer.class, codigo);
        return n != null && n > 0;
    }

    /** Inserta una fila en ACTUAL.DBF mapeando desde tu entidad Activo. */
    public void insertarDesdeActivo(Activo a, String entidadCode, String unidadCode, String usuario) {
        // --- mapeo (puedes dejar tu lógica igual) ---
        String ENTIDAD = entidadCode;
        String UNIDAD = unidadCode;
        String CODIGO = a.getCodigo();
        String CODIGOSEC = a.getCodigoSec();
        String DESCRIP = a.getDescripcion();

        // CODCONT: conviértelo a número si la columna en DBF es numérica
        Integer CODCONT = null;
        if (a.getGrupoContable() != null && a.getGrupoContable().getCodContable() != null) {
            try {
                CODCONT = Integer.valueOf(a.getGrupoContable().getCodContable().toString().trim());
            } catch (Exception ignore) {
            }
        }

        Short CODAUX = null;
        if (a.getAuxiliar() != null && a.getAuxiliar().getCodAux() != null) {
            try {
                CODAUX = Short.valueOf(a.getAuxiliar().getCodAux().toString().trim());
            } catch (Exception ignore) {
            }
        }

        String ORG_FIN = (a.getOrganismoFinanciero() != null)
                ? String.valueOf(a.getOrganismoFinanciero().getIdOrganismoFinanciero())
                : null;

        Short CODOFIC = null;
        if (a.getOficina() != null && a.getOficina().getCodOfi() != null) {
            try {
                CODOFIC = Short.valueOf(a.getOficina().getCodOfi().toString().trim());
            } catch (Exception ignore) {
            }
        }

        // Si la columna CODRESP del DBF es NUMÉRICA: envíala como Integer
        Integer CODRESP = null;
        if (a.getResponsable() != null && a.getResponsable().getPersona() != null) {
            String ci = a.getResponsable().getPersona().getCi();
            if (ci != null) {
                String onlyDigits = ci.replaceAll("\\D+", "");
                if (!onlyDigits.isEmpty()) {
                    try {
                        CODRESP = Integer.valueOf(onlyDigits);
                    } catch (Exception ignore) {
                    }
                }
            }
        }

        Double COSTO = a.getCosto();
        Integer VIDAUT = null; // tu campo era BigDecimal; pásalo a Integer si la columna es entera
        if (a.getVidaUtil() != null) {
            try {
                VIDAUT = new java.math.BigDecimal(a.getVidaUtil().toString()).intValue();
            } catch (Exception ignore) {
            }
        }

        LocalDate FEC = (a.getFechaAdquisicion() != null) ? a.getFechaAdquisicion() : LocalDate.now();
        Integer DIA = FEC.getDayOfMonth();
        Integer MES = FEC.getMonthValue();
        Integer ANO = FEC.getYear();

        Short CODESTADO = 1;
        Short API_ESTADO = 1;
        String USUAR = (usuario != null ? usuario : "SISTEMA");

        String OBSERV = null;
        Double DEPACU = 0d;

        String sql = """
                    INSERT INTO ACTUAL
                    (ENTIDAD, UNIDAD, CODIGO, CODIGOSEC, DESCRIP, COSTO, DEPACU, VIDAUTIL, MES, ANO, DIA,
                     CODOFIC, CODRESP, OBSERV, CODCONT, CODAUX, ORG_FIN, FEULT, USUAR, API_ESTADO, CODESTADO)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """;

        // === COPIAS FINALES PARA LA LAMBDA ===
        final String vENTIDAD = ENTIDAD;
        final String vUNIDAD = UNIDAD;
        final String vCODIGO = CODIGO;
        final String vCODIGOSEC = CODIGOSEC;
        final String vDESCRIP = DESCRIP;

        final Double vCOSTO = COSTO;
        final Double vDEPACU = DEPACU;
        final Integer vVIDAUT = VIDAUT;
        final Integer vMES = MES;
        final Integer vANO = ANO;
        final Integer vDIA = DIA;

        final Short vCODOFIC = CODOFIC;
        final Integer vCODRESP = CODRESP; // numérico si la columna es numérica
        final String vOBSERV = OBSERV;
        final Integer vCODCONT = CODCONT; // numérico
        final Short vCODAUX = CODAUX; // numérico
        final String vORG_FIN = ORG_FIN;
        final java.sql.Date vFEULT = java.sql.Date.valueOf(FEC);
        final String vUSUAR = USUAR;
        final Short vAPI_ESTADO = API_ESTADO;
        final Short vCODESTADO = CODESTADO;

        synchronized (actualLock) {
            dbfJdbc.update(con -> {
                PreparedStatement ps = con.prepareStatement(sql);
                int i = 1;
                setParam(ps, i++, vENTIDAD, Types.VARCHAR);
                setParam(ps, i++, vUNIDAD, Types.VARCHAR);
                setParam(ps, i++, vCODIGO, Types.VARCHAR);
                setParam(ps, i++, vCODIGOSEC, Types.VARCHAR);
                setParam(ps, i++, vDESCRIP, Types.VARCHAR);
                setParam(ps, i++, vCOSTO, Types.DOUBLE);
                setParam(ps, i++, vDEPACU, Types.DOUBLE);
                setParam(ps, i++, vVIDAUT, Types.INTEGER);
                setParam(ps, i++, vMES, Types.INTEGER);
                setParam(ps, i++, vANO, Types.INTEGER);
                setParam(ps, i++, vDIA, Types.INTEGER);
                setParam(ps, i++, vCODOFIC, Types.INTEGER);
                setParam(ps, i++, vCODRESP, Types.INTEGER); // <— numérico si la columna lo es
                setParam(ps, i++, vOBSERV, Types.VARCHAR);
                setParam(ps, i++, vCODCONT, Types.INTEGER);
                setParam(ps, i++, vCODAUX, Types.INTEGER);
                setParam(ps, i++, vORG_FIN, Types.VARCHAR);
                setParam(ps, i++, vFEULT, Types.DATE);
                setParam(ps, i++, vUSUAR, Types.VARCHAR);
                setParam(ps, i++, vAPI_ESTADO, Types.INTEGER);
                setParam(ps, i++, vCODESTADO, Types.INTEGER);
                return ps;
            });
        }
    }

}
