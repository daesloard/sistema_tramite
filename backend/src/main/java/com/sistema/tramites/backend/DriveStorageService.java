package com.sistema.tramites.backend;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.ByteArrayContent;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.Collections;

@Service
public class DriveStorageService {

    private static final String APP_NAME = "sistema-tramites";

    @Value("${app.storage.drive.enabled:false}")
    private boolean driveEnabled;

    @Value("${app.storage.drive.credentials-json-base64:}")
    private String credentialsJsonBase64;

    @Value("${app.storage.drive.folder-id:}")
    private String folderId;

    private volatile Drive driveClient;

    public boolean isEnabled() {
        return driveEnabled && credentialsJsonBase64 != null && !credentialsJsonBase64.isBlank();
    }

    public String uploadFile(String fileName, String contentType, byte[] bytes) throws IOException {
        if (!isEnabled()) {
            throw new IllegalStateException("Google Drive storage no está habilitado");
        }

        try {
            Drive drive = getDriveClient();
            com.google.api.services.drive.model.File fileMetadata = new com.google.api.services.drive.model.File();
            fileMetadata.setName((fileName == null || fileName.isBlank()) ? "documento.bin" : fileName);
            if (folderId != null && !folderId.isBlank()) {
                fileMetadata.setParents(Collections.singletonList(folderId.trim()));
            }

            String mimeType = (contentType == null || contentType.isBlank())
                    ? "application/octet-stream"
                    : contentType;

            ByteArrayContent mediaContent = new ByteArrayContent(mimeType, bytes);
            com.google.api.services.drive.model.File created = drive.files()
                    .create(fileMetadata, mediaContent)
                    .setFields("id")
                    .execute();

            return created.getId();
        } catch (GeneralSecurityException e) {
            throw new IOException("No fue posible inicializar cliente de Google Drive", e);
        }
    }

    public byte[] downloadFile(String fileId) throws IOException {
        if (!isEnabled()) {
            throw new IllegalStateException("Google Drive storage no está habilitado");
        }

        try {
            Drive drive = getDriveClient();
            try (InputStream inputStream = drive.files().get(fileId).executeMediaAsInputStream()) {
                return inputStream.readAllBytes();
            }
        } catch (GeneralSecurityException e) {
            throw new IOException("No fue posible inicializar cliente de Google Drive", e);
        }
    }

    private Drive getDriveClient() throws IOException, GeneralSecurityException {
        Drive client = driveClient;
        if (client != null) {
            return client;
        }

        synchronized (this) {
            if (driveClient != null) {
                return driveClient;
            }

            byte[] credentialsBytes = Base64.getDecoder().decode(credentialsJsonBase64.trim());
            try (InputStream credentialsStream = new ByteArrayInputStream(credentialsBytes)) {
                GoogleCredentials credentials = GoogleCredentials.fromStream(credentialsStream)
                        .createScoped(Collections.singleton(DriveScopes.DRIVE_FILE));

                NetHttpTransport transport = GoogleNetHttpTransport.newTrustedTransport();
                driveClient = new Drive.Builder(
                        transport,
                        GsonFactory.getDefaultInstance(),
                        new HttpCredentialsAdapter(credentials)
                ).setApplicationName(APP_NAME).build();
            }

            return driveClient;
        }
    }
}
