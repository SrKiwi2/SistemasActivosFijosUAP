package com.usic.SistemasActivosFijosUAP.model.service;

import java.io.IOException;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
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
        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet hoja = workbook.getSheetAt(0);
            for (int i = 1; i <= hoja.getLastRowNum(); i++) {
                Row fila = hoja.getRow(i);

                if (fila == null) continue;

                String nombre = fila.getCell(0).getStringCellValue().trim();
                String codigo = fila.getCell(1).getStringCellValue().trim();
                String prefijo = fila.getCell(2).getStringCellValue().trim();

                Predio predio = predioServicio.buscarPorPrefijo(prefijo);

                Oficina oficina = new Oficina();
                oficina.setNombre(nombre);
                oficina.setCodigo(codigo);
                oficina.setPredio(predio);

                oficinaService.save(oficina);
            }
        }
    }
}
