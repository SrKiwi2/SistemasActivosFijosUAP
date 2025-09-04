package com.usic.SistemasActivosFijosUAP.model.service;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
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

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ActivoExcelService {

    /* ESTO YA NO SE UTILIZA, YA NO ES POR EXCEL */

    @Data
    @AllArgsConstructor
    private static class ErrorImportacion {
        private int fila;
        private String motivo;
    }
    
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
                Double costo = getNumeric(fila.getCell(3));

                Cell vidaUtilCell = fila.getCell(4);
                Integer vidaUtil = null;

                if (vidaUtilCell != null) {
                    if (vidaUtilCell.getCellType() == CellType.NUMERIC) {
                        vidaUtil = (int) vidaUtilCell.getNumericCellValue();
                    } else if (vidaUtilCell.getCellType() == CellType.STRING) {
                        try {
                            vidaUtil = Integer.parseInt(vidaUtilCell.getStringCellValue().trim());
                        } catch (NumberFormatException e) {
                            System.out.println("⚠ Vida útil no válida: " + vidaUtilCell.getStringCellValue());
                        }
                    }
                }

                if (vidaUtil == null) {
                    System.out.println("Vida util no procesada: " + vidaUtil);
                    continue;
                }
                
                int dia = (int) fila.getCell(5).getNumericCellValue();
                int mes = (int) fila.getCell(6).getNumericCellValue();
                int anio = (int) fila.getCell(7).getNumericCellValue();
                LocalDate fechaAdq = LocalDate.of(anio, mes, dia);

                String estadoActivoCodigo = getText(fila.getCell(8)).trim().toUpperCase();
                String grupoContableCodigo = getText(fila.getCell(9)).trim().toUpperCase();
                String oficinaNombre = getText(fila.getCell(10));
                String nombreResponsableCompleto = getText(fila.getCell(11));
                String[] datosPersona = separarNombreCompleto(nombreResponsableCompleto);
                String nombreResponsable = datosPersona[0];
                String paterno = datosPersona[1];
                String materno = datosPersona[2];
                String cargoNombre = getText(fila.getCell(12));

                // List<Persona> personas = personaService.buscarPersonaPorNombrePaternoMaterno(nombreResponsable, paterno, materno);
                // Persona persona = personas.isEmpty() ? null : personas.get(0);


                Persona persona = null;

                if (!nombreResponsable.isBlank() && !paterno.isBlank() && !materno.isBlank()) {
                    persona = personaService.buscarPersonaPorNombreCompletoUno(nombreResponsable, paterno, materno);
                }else if (!nombreResponsable.isBlank() && !paterno.isBlank()) {
                    persona = personaService.buscarPersonaPorNombrePaterno(nombreResponsable, paterno);
                }else if (!nombreResponsable.isBlank()) {
                    persona = personaService.buscarPersonaNombre(nombreResponsable);
                }

                if (persona == null) {
                    persona = new Persona();
                    persona.setNombre(nombreResponsable.isBlank() ? null : nombreResponsable);
                    persona.setPaterno(paterno.isBlank() ? null : paterno);
                    persona.setMaterno(materno.isBlank() ? null : materno);
                    persona.setEstado("ACTIVO");
                    persona.setRegistro(new Date());
                    persona.setRegistroIdUsuario(1L);
                    personaService.save(persona);
                    System.out.println("PERSONA CREADA: "+ persona.getNombre());
                }

                Cargo cargo = cargoService.buscarPorNombre(cargoNombre);
                if (cargo == null) {
                    cargo = new Cargo();
                    cargo.setNombre(cargoNombre);
                    cargo.setEstado("ACTIVO");
                    cargo.setRegistroIdUsuario(1L);
                    cargo.setRegistro(new Date());
                    cargoService.save(cargo);
                }

                // Oficina oficina = oficinaService.buscarPorNombre(oficinaNombre);
                // if (oficina == null) {
                //     System.out.println("no se encontro la oficina: " + oficina);
                //     continue;
                // }

                // Responsable responsable = responsableService.responsablePersonaOficinaCargo(persona, oficina, cargo);
                // if (responsable == null) {
                //     responsable = new Responsable();
                //     responsable.setPersona(persona);
                //     responsable.setCargo(cargo);
                //     responsable.setOficina(oficina);
                //     responsable.setRegistro(new Date());
                //     responsable.setEstado("ACTIVO");
                //     responsable.setRegistroIdUsuario(1L);
                //     responsableService.save(responsable);
                // }

                EstadoActivo estado = estadoActivoService.buscarPorCodigo(estadoActivoCodigo);
                // GrupoContable grupo = grupoContableService.buscarPorCodigo(grupoContableCodigo);

                // if (estado == null || grupo == null) {
                //     System.out.println("no se encontro el estado: " + estado + " o no se encontro el grupo contable: " + grupo);
                //     continue;
                // }

                Activo activo = new Activo();
                activo.setCodigo("148-"+codigo);
                activo.setNombre(nombre);
                activo.setDescripcion(descripcion);
                activo.setCosto(costo);
                activo.setVidaUtil(vidaUtil);
                activo.setFechaAdquisicion(fechaAdq);
                activo.setEstadoActivo(estado);
                // activo.setGrupoContable(grupo);
                // activo.setOficina(oficina);
                // activo.setResponsable(responsable);
                activo.setEstado("ACTIVO");
                activo.setRegistro(new Date());
                activo.setRegistroIdUsuario(1L);
                activoService.save(activo);

                System.out.println("Registro procesador: " + i);
            }

        } catch (Exception e) {
            
            e.printStackTrace();
            throw new IOException("Error al procesar archivo", e);
        }
    }

    private String getText(Cell cell) {
        if (cell == null) return "";

        if (cell.getCellType() == CellType.STRING) {
            return cell.getStringCellValue().trim();
        }

        if (cell.getCellType() == CellType.NUMERIC) {
            DataFormatter formatter = new DataFormatter();
            return formatter.formatCellValue(cell).trim();
        }
        return "";
    }


    private String[] separarNombreCompleto(String nombreCompleto) {
        nombreCompleto.trim().replaceAll("\\s+", " ");
        String[] partes = nombreCompleto.trim().split("\\s+");
        String nombre = "", paterno = "", materno = "";

        for (int i = 0; i < partes.length; i++) {
            partes[i] = capitalizar(partes[i]);
        }
    
        if (partes.length >= 4) {
            nombre = partes[0] + " " + partes[1];
            paterno = partes[partes.length - 2];
            materno = partes[partes.length - 1];
        } else if (partes.length == 3) {
            nombre = partes[0];
            paterno = partes[1];
            materno = partes[2];
        } else if (partes.length == 2) {
            nombre = partes[0];
            paterno = partes[1];
            materno = "";
        } else if (partes.length == 1) {
            nombre = partes[0];
        } else {
            nombre = nombreCompleto;
        }
        return new String[] { nombre, paterno, materno };
    }
    
    private String capitalizar(String palabra) {
        if (palabra == null || palabra.isEmpty()) return palabra;
        return palabra.substring(0, 1).toUpperCase() + palabra.substring(1).toLowerCase();
    }

    private Double getNumeric(Cell cell) {
        if (cell == null) return 0.0;
        if (cell.getCellType() == CellType.NUMERIC) return cell.getNumericCellValue();
        if (cell.getCellType() == CellType.STRING) {
            try {
                return Double.parseDouble(cell.getStringCellValue().trim().replace(",", "."));
            } catch (NumberFormatException e) {
                return 0.0;
            }
        }
        return 0.0;
    }
}