package com.usic.SistemasActivosFijosUAP.model.service;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.FileContent;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;

public class DriveUploader {
    private static final String APPLICATION_NAME = "ActivosUAPDriveUploader";
    private static final String CREDENTIALS_JSON_PATH = "src/main/resources/static/assets/json/sistema-activos-fijos-7634e7990ade.json"; // Coloca en la raíz del proyecto
    private static final String MIME_TYPE_PDF = "application/pdf";

    public static String uploadPdf(byte[] pdfBytes, String nombreArchivo, String carpetaDestinoId) throws Exception {
        java.io.File tempFile = java.io.File.createTempFile("activo_", ".pdf");
        try (OutputStream os = new FileOutputStream(tempFile)) {
            os.write(pdfBytes);
        }

        GoogleCredential credential = GoogleCredential
                .fromStream(Files.newInputStream(Path.of(CREDENTIALS_JSON_PATH)))
                .createScoped(Collections.singleton("https://www.googleapis.com/auth/drive"));

        HttpRequestInitializer requestInitializer = request -> {
            credential.initialize(request);
            request.setConnectTimeout(3 * 60000); // 3 minutos
            request.setReadTimeout(3 * 60000);    // 3 minutos
        };

        Drive driveService = new Drive.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                JacksonFactory.getDefaultInstance(),
                requestInitializer
        ).setApplicationName(APPLICATION_NAME).build();

        FileList result = driveService.files().list()
            .setQ("mimeType = 'application/vnd.google-apps.folder' and name = 'ACTIVOS FIJOS pdf'")
            .setFields("files(id, name, owners)")
            .execute();

        for (File file : result.getFiles()) {
            System.out.println("Carpeta encontrada: " + file.getName() + " - ID: " + file.getId() + " - Dueño: " + file.getOwners());
        }

        File metadata = new File();
        metadata.setName(nombreArchivo);
        metadata.setParents(Collections.singletonList(carpetaDestinoId));

        FileContent fileContent = new FileContent(MIME_TYPE_PDF, tempFile);

        File uploadedFile = driveService.files().create(metadata, fileContent)
                .setFields("id, webViewLink")
                .execute();

        tempFile.delete(); // Limpieza

        return uploadedFile.getWebViewLink(); // Link público para ver el PDF
    }
}
