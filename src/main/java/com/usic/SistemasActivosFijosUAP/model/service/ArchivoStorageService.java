package com.usic.SistemasActivosFijosUAP.model.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Almacenamiento de archivos adjuntos en el filesystem local.
 *
 * Sigue el patrón ya usado en el proyecto (carpetas servidas por ResourceHandler).
 * Los archivos se guardan bajo {@code uploads/<subcarpeta>/} (relativo al directorio
 * de trabajo del proceso) y se exponen vía la URL pública {@code /uploads/<subcarpeta>/<archivo>}
 * registrada en {@link com.usic.SistemasActivosFijosUAP.config.MvcConfig}.
 *
 * Subcarpetas usadas:
 *  - {@code baja/informes}   → informe de la unidad de hardware (baja de activos)
 *  - {@code ajenos/notas}    → nota del inmediato superior (ingreso de bienes ajenos)
 *  - {@code ajenos/fotos}    → fotografías de los activos ajenos
 */
@Service
public class ArchivoStorageService {

    /** Raíz física donde se escriben los archivos (relativa al directorio de ejecución). */
    private static final String RAIZ = "uploads";
    /** Prefijo público con el que el ResourceHandler sirve los archivos. */
    private static final String URL_BASE = "/uploads";

    /**
     * Guarda un archivo subido bajo {@code uploads/<subcarpeta>/}.
     *
     * @param file       archivo recibido; si es {@code null} o está vacío se ignora y retorna {@code null}.
     * @param subcarpeta carpeta lógica de destino (p. ej. {@code "baja/informes"}).
     * @param baseNombre prefijo legible para el nombre final (se sanitiza).
     * @return URL pública relativa ({@code /uploads/...}) o {@code null} si no había archivo.
     */
    public String guardar(MultipartFile file, String subcarpeta, String baseNombre) {
        if (file == null || file.isEmpty()) {
            return null;
        }
        try {
            Path carpeta = Paths.get(RAIZ, subcarpeta).toAbsolutePath().normalize();
            Files.createDirectories(carpeta);

            String ext = extension(file.getOriginalFilename());
            String nombre = sanitizar(baseNombre) + "_" + System.currentTimeMillis() + ext;

            Path destino = carpeta.resolve(nombre).normalize();
            // Seguridad: evita escapar de la carpeta destino con nombres maliciosos.
            if (!destino.startsWith(carpeta)) {
                throw new IllegalArgumentException("Ruta de archivo inválida.");
            }

            try (var in = file.getInputStream()) {
                Files.copy(in, destino, StandardCopyOption.REPLACE_EXISTING);
            }

            return URL_BASE + "/" + subcarpeta + "/" + nombre;
        } catch (IOException ex) {
            throw new RuntimeException("No se pudo guardar el archivo '" + file.getOriginalFilename() + "': " + ex.getMessage(), ex);
        }
    }

    /** Devuelve la extensión con punto (p. ej. {@code ".pdf"}) o cadena vacía. */
    private String extension(String nombreOriginal) {
        if (nombreOriginal == null) {
            return "";
        }
        int i = nombreOriginal.lastIndexOf('.');
        if (i < 0 || i == nombreOriginal.length() - 1) {
            return "";
        }
        String ext = nombreOriginal.substring(i).toLowerCase(Locale.ROOT);
        // Sólo conserva extensiones razonables (letras/números).
        return ext.matches("\\.[a-z0-9]{1,6}") ? ext : "";
    }

    /** Limpia el prefijo para que sea seguro como nombre de archivo. */
    private String sanitizar(String base) {
        String limpio = (base == null || base.isBlank()) ? "archivo" : base.trim();
        limpio = limpio.replaceAll("[^a-zA-Z0-9_-]", "_");
        return limpio.length() > 40 ? limpio.substring(0, 40) : limpio;
    }
}
