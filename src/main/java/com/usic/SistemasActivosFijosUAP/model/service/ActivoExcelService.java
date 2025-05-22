package com.usic.SistemasActivosFijosUAP.model.service;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.usic.SistemasActivosFijosUAP.model.IService.IActivoService;
import com.usic.SistemasActivosFijosUAP.model.IService.ICargoService;
import com.usic.SistemasActivosFijosUAP.model.IService.IEstadoActivoService;
import com.usic.SistemasActivosFijosUAP.model.IService.IGrupoContableService;
import com.usic.SistemasActivosFijosUAP.model.IService.IOficinaService;
import com.usic.SistemasActivosFijosUAP.model.IService.IPersonaService;
import com.usic.SistemasActivosFijosUAP.model.IService.IResponsableService;
import com.usic.SistemasActivosFijosUAP.model.entity.Activo;
import com.usic.SistemasActivosFijosUAP.model.entity.Cargo;
import com.usic.SistemasActivosFijosUAP.model.entity.EstadoActivo;
import com.usic.SistemasActivosFijosUAP.model.entity.GrupoContable;
import com.usic.SistemasActivosFijosUAP.model.entity.Oficina;
import com.usic.SistemasActivosFijosUAP.model.entity.Persona;
import com.usic.SistemasActivosFijosUAP.model.entity.Responsable;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ActivoExcelService {
    
    private final IOficinaService oficinaService;
    private final IPersonaService personaService;
    private final ICargoService cargoService;
    private final IResponsableService responsableService;
    private final IEstadoActivoService estadoActivoService;
    private final IGrupoContableService grupoContableService;
    private final IActivoService activoService;

    public void cargarActivosDesdeExcel(MultipartFile file) throws IOException {
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet hoja = workbook.getSheetAt(0);

            for (int i = 1; i <= hoja.getLastRowNum(); i++) {
                Row fila = hoja.getRow(i);
                if (fila == null) continue;

                String codigo = getText(fila.getCell(0));
                String nombre = getText(fila.getCell(1));
                String descripcion = getText(fila.getCell(2));
                Double costo = fila.getCell(3).getNumericCellValue();
                Integer vidaUtil = (int) fila.getCell(4).getNumericCellValue();

                int dia = (int) fila.getCell(5).getNumericCellValue();
                int mes = (int) fila.getCell(6).getNumericCellValue();
                int anio = (int) fila.getCell(7).getNumericCellValue();
                LocalDate fechaAdq = LocalDate.of(anio, mes, dia);

                String estadoActivoCodigo = getText(fila.getCell(8));
                String grupoContableCodigo = getText(fila.getCell(9));
                String oficinaCodigo = getText(fila.getCell(10));

                String nombreResponsable = getText(fila.getCell(11));
                String paterno = getText(fila.getCell(12));
                String materno = getText(fila.getCell(13));
                String cargoNombre = getText(fila.getCell(14));

                // Buscar o crear la persona
                Persona persona = personaService.buscarPersonaPorNombrePaternoMaterno(nombreResponsable, paterno, materno);
                if (persona == null) {
                    persona = new Persona();
                    persona.setNombre(nombreResponsable);
                    persona.setPaterno(paterno);
                    persona.setMaterno(materno);
                    persona.setEstado("ACTIVO");
                    persona.setRegistro(new Date());
                    persona.setRegistroIdUsuario(1L);
                    personaService.save(persona);
                }

                // Buscar o crear el cargo
                Cargo cargo = cargoService.buscarPorNombre(cargoNombre);
                if (cargo == null) {
                    cargo = new Cargo();
                    cargo.setNombre(cargoNombre);
                    cargo.setEstado("ACTIVO");
                    cargo.setRegistroIdUsuario(1L);
                    cargo.setRegistro(new Date());
                    cargoService.save(cargo);
                }

                // Buscar oficina
                Oficina oficina = oficinaService.buscarPorCodigo(oficinaCodigo);
                if (oficina == null) {
                    System.out.println("Oficina no encontrada para código: " + oficinaCodigo);
                    continue;
                }

                // Buscar o crear responsable
                Responsable responsable = responsableService.responsablePersonaOficinaCargo(persona, oficina, cargo);
                if (responsable == null) {
                    responsable = new Responsable();
                    responsable.setPersona(persona);
                    responsable.setCargo(cargo);
                    responsable.setOficina(oficina);
                    responsable.setRegistro(new Date());
                    responsable.setEstado("ACTIVO");
                    responsable.setRegistroIdUsuario(1L);
                    responsableService.save(responsable);
                }

                // Buscar estado activo y grupo contable
                EstadoActivo estado = estadoActivoService.buscarPorCodigo(estadoActivoCodigo);
                GrupoContable grupo = grupoContableService.buscarPorCodigo(grupoContableCodigo);

                if (estado == null || grupo == null) {
                    System.out.println("Estado o grupo contable no encontrado para activo: " + nombre);
                    continue;
                }

                // Crear activo
                Activo activo = new Activo();
                activo.setCodigo(codigo);
                activo.setNombre(nombre);
                activo.setDescripcion(descripcion);
                activo.setCosto(costo);
                activo.setVida_util(vidaUtil);
                activo.setFecha_adquisición(fechaAdq);
                activo.setEstadoActivo(estado);
                activo.setGrupoContable(grupo);
                activo.setOficina(oficina);
                activo.setResponsable(responsable);

                activo.setEstado("ACTIVO");
                activo.setRegistro(new Date());
                activo.setRegistroIdUsuario(1L);

                activoService.save(activo);
                System.out.println("Activo registrado: " + nombre);
            }

        } catch (Exception e) {
            throw new IOException("Error al procesar archivo", e);
        }
    }

    private String getText(Cell cell) {
        if (cell == null) return "";
        if (cell.getCellType() == CellType.STRING) return cell.getStringCellValue().trim();
        if (cell.getCellType() == CellType.NUMERIC) return String.valueOf((int) cell.getNumericCellValue());
        return "";
    }
}
