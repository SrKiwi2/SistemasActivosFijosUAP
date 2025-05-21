package com.usic.SistemasActivosFijosUAP.model.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.usic.SistemasActivosFijosUAP.model.IService.IOficinaService;
import com.usic.SistemasActivosFijosUAP.model.IService.IPredioServicio;
import com.usic.SistemasActivosFijosUAP.model.entity.Oficina;
import com.usic.SistemasActivosFijosUAP.model.entity.Predio;



import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OficinaExcelService {

    private final IPredioServicio predioServicio;
    private final IOficinaService oficinaService;

    public void cargarDesdeExcel(MultipartFile file) throws IOException {
        Set<String> nombresProcesados = new HashSet<>(); // para evitar duplicados dentro del archivo

        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet hoja = workbook.getSheetAt(0);

            for (int i = 1; i <= hoja.getLastRowNum(); i++) {
                Row fila = hoja.getRow(i);
                if (fila == null) continue;

                String prefijo = obtenerValorCeldaComoTexto(fila.getCell(0));
                String codigo = obtenerValorCeldaComoTexto(fila.getCell(1));
                String nombre = obtenerValorCeldaComoTexto(fila.getCell(2));

                // Evitar duplicados en el Excel
                if (nombresProcesados.contains(nombre)) {
                    System.out.println("Nombre duplicado en archivo: " + nombre + " - Omitiendo...");
                    continue;
                }
                nombresProcesados.add(nombre);

                // Verificar si ya existe en la base de datos
                Oficina yaExiste = oficinaService.buscarPorNombre(nombre);
                if (yaExiste != null) {
                    System.out.println("Oficina ya existe en BD: " + nombre + " - Omitiendo...");
                    continue;
                }

                Predio predio = predioServicio.buscarPorPrefijo(prefijo);
                if (predio == null) {
                    System.out.println("No se encontró predio con prefijo: " + prefijo + " - Omitiendo...");
                    continue;
                }

                Oficina oficina = new Oficina();
                oficina.setNombre(nombre);
                oficina.setCodigo(codigo);
                oficina.setPredio(predio);
                oficina.setEstado("ACTIVO");
                oficina.setRegistro(new Date());
                oficina.setRegistroIdUsuario(1L);
                oficinaService.save(oficina);
                System.out.println("Oficina registrada: " + nombre);
            }
        } catch (Exception e) {
            throw new IOException("Archivo no válido o dañado", e);
        }
    }

    private String obtenerValorCeldaComoTexto(Cell celda) {
        if (celda == null) return "";

        switch (celda.getCellType()) {
            case STRING:
                return celda.getStringCellValue().trim();
            case NUMERIC:
                // Si es un número entero, quitamos decimales
                if (DateUtil.isCellDateFormatted(celda)) {
                    return celda.getDateCellValue().toString();
                } else {
                    double valor = celda.getNumericCellValue();
                    if (valor == (int) valor) {
                        return String.valueOf((int) valor);
                    } else {
                        return String.valueOf(valor);
                    }
                }
            case BOOLEAN:
                return String.valueOf(celda.getBooleanCellValue());
            case FORMULA:
                return celda.getCellFormula();
            default:
                return "";
        }
    }
}

