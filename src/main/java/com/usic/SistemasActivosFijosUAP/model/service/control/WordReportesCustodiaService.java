package com.usic.SistemasActivosFijosUAP.model.service.control;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.springframework.stereotype.Service;

import com.usic.SistemasActivosFijosUAP.model.dto.control.CustodiaDTOs;
import com.usic.SistemasActivosFijosUAP.model.entity.Activo;
import com.usic.SistemasActivosFijosUAP.model.repository.CustodiaFaltantesRepo;
import com.usic.SistemasActivosFijosUAP.model.service.documento.MembreteWord;
import com.usic.SistemasActivosFijosUAP.model.service.documento.WordDocumentos;

import lombok.RequiredArgsConstructor;

/** Reportes de custodia editables en Word, con las mismas consultas que Excel. */
@Service
@RequiredArgsConstructor
public class WordReportesCustodiaService {
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private final CustodiaFaltantesRepo repo;

    public byte[] custodiaPredioWord(Long idPredio, String usuario) throws Exception {
        CustodiaDTOs.PredioCustodia p = repo.prediosConCustodia().stream()
                .filter(x -> Objects.equals(x.idPredio(), idPredio)).findFirst()
                .orElseThrow(() -> new ReglaNegocioException("Ese predio no tiene oficina de faltantes."));
        List<CustodiaDTOs.BienCustodia> bienes = repo.bienesEnCustodia(idPredio);
        try (XWPFDocument doc = documento("REPORTE DE CUSTODIA DE FALTANTES", usuario)) {
            parrafo(doc, "Predio: " + valor(p.unidad()) + " — " + valor(p.predio())
                    + " · Oficina: " + p.codOfi() + " — " + valor(p.oficina()), true);
            long enCustodia = bienes.stream().filter(b -> "En custodia".equals(b.situacion())).count();
            long pendienteBaja = bienes.stream().filter(b -> "RESUELTO".equals(b.estadoHallazgo())).count();
            long historicos = bienes.stream().filter(b -> b.estadoHallazgo() == null).count();
            parrafo(doc, "Total: " + bienes.size() + " · En custodia: " + enCustodia
                    + " · Resueltos, pendientes de baja: " + pendienteBaja
                    + " · Históricos sin registro: " + historicos
                    + " · Trasladándose: " + (bienes.size() - enCustodia - pendienteBaja - historicos), false);
            if (bienes.isEmpty()) {
                parrafo(doc, "La oficina de faltantes está vacía: el predio no tiene faltantes en custodia.", false);
            } else {
                Map<String, List<CustodiaDTOs.BienCustodia>> grupos = new LinkedHashMap<>();
                for (CustodiaDTOs.BienCustodia b : bienes) {
                    String persona = valor(b.persona());
                    if (b.ci() != null) persona += " (C.I. " + b.ci() + ")";
                    grupos.computeIfAbsent(persona, k -> new java.util.ArrayList<>()).add(b);
                }
                int n = 0;
                for (var grupo : grupos.entrySet()) {
                    parrafo(doc, grupo.getKey() + " · " + grupo.getValue().size() + " bien(es)", true);
                    XWPFTable t = WordDocumentos.tabla(doc, "N.º", "Código", "Descripción", "Oficina de origen", "Acta", "Situación");
                    for (CustodiaDTOs.BienCustodia b : grupo.getValue()) {
                        WordDocumentos.fila(t, String.valueOf(++n), valor(Activo.codigoDocumento(b.codigo())),
                                valor(b.descripcion()), b.oficinaOrigen() == null ? "—" : b.codOfiOrigen() + " — " + b.oficinaOrigen(),
                                valor(b.numeroActa()), b.situacion());
                    }
                }
            }
            return WordDocumentos.bytes(doc);
        }
    }

    public byte[] consolidadoWord(boolean soloPendientes, String usuario) throws Exception {
        List<CustodiaDTOs.ConsolidadoFila> filas = repo.consolidado(soloPendientes);
        try (XWPFDocument doc = documento("CONSOLIDADO DE FALTANTES POR PERSONA", usuario)) {
            parrafo(doc, soloPendientes ? "Personas con faltantes sin aclarar" : "Todos los faltantes registrados (sin anulados)", false);
            long personas = filas.stream().map(CustodiaDTOs.ConsolidadoFila::idPersona).distinct().count();
            parrafo(doc, "Personas: " + personas
                    + " · Abiertos: " + filas.stream().mapToLong(CustodiaDTOs.ConsolidadoFila::abiertos).sum()
                    + " · En custodia: " + filas.stream().mapToLong(CustodiaDTOs.ConsolidadoFila::enCustodia).sum()
                    + " · Resueltos: " + filas.stream().mapToLong(CustodiaDTOs.ConsolidadoFila::resueltos).sum(), true);
            if (filas.isEmpty()) {
                parrafo(doc, "No hay faltantes con este alcance.", false);
            } else {
                XWPFTable t = WordDocumentos.tabla(doc, "N.º", "Persona / C.I.", "Predio", "Abiertos", "En custodia", "Resueltos", "Total");
                int n = 0;
                Long anterior = null;
                for (CustodiaDTOs.ConsolidadoFila f : filas) {
                    boolean nueva = !Objects.equals(anterior, f.idPersona());
                    if (nueva) n++;
                    anterior = f.idPersona();
                    WordDocumentos.fila(t, nueva ? String.valueOf(n) : "",
                            nueva ? valor(f.persona()) + "\nC.I. " + valor(f.ci()) : "",
                            valor(f.unidad()) + " — " + valor(f.predio()),
                            String.valueOf(f.abiertos()), String.valueOf(f.enCustodia()),
                            String.valueOf(f.resueltos()), String.valueOf(f.total()));
                }
            }
            return WordDocumentos.bytes(doc);
        }
    }

    private XWPFDocument documento(String titulo, String usuario) {
        XWPFDocument doc = new XWPFDocument();
        WordDocumentos.pagina(doc, true, 1700, 1200);
        MembreteWord.aplicar(doc);
        WordDocumentos.parrafo(doc, titulo, 13, true, ParagraphAlignment.CENTER);
        parrafo(doc, "Emitido: " + LocalDateTime.now().format(FECHA) + " · Usuario: " + valor(usuario), false);
        return doc;
    }

    private static void parrafo(XWPFDocument doc, String texto, boolean negrita) {
        WordDocumentos.parrafo(doc, texto, 9, negrita, ParagraphAlignment.LEFT);
    }

    private static String valor(String texto) {
        return texto == null || texto.isBlank() ? "—" : texto;
    }
}
