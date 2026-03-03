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
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    public Map<String, Object> getDiagnostics() {
        Map<String, Object> diagnostics = new HashMap<>();
        diagnostics.put("driveEnabledFlag", driveEnabled);
        diagnostics.put("hasCredentials", credentialsJsonBase64 != null && !credentialsJsonBase64.isBlank());
        diagnostics.put("hasFolderId", folderId != null && !folderId.isBlank());
        diagnostics.put("isEnabled", isEnabled());
        diagnostics.put("folderId", folderId == null ? "" : folderId.trim());

        if (!isEnabled()) {
            diagnostics.put("ok", false);
            diagnostics.put("reason", "Drive no habilitado por configuración");
            return diagnostics;
        }

        try {
            byte[] credentialBytes = decodeCredentialsBytes();
            String credentialsText = new String(credentialBytes, StandardCharsets.UTF_8);
            diagnostics.put("credentialsMode", credentialsText.trim().startsWith("{") ? "json-raw|base64-decodificado" : "desconocido");
            diagnostics.put("credentialsPreview", extraerClientEmail(credentialsText));

            Drive drive = getDriveClient();
            if (folderId != null && !folderId.isBlank()) {
                com.google.api.services.drive.model.File folder = drive.files()
                        .get(folderId.trim())
                        .setSupportsAllDrives(true)
                        .setFields("id,name,mimeType")
                        .execute();
                diagnostics.put("folderAccessible", true);
                diagnostics.put("folderName", folder.getName());
                diagnostics.put("folderMimeType", folder.getMimeType());
            } else {
                diagnostics.put("folderAccessible", false);
                diagnostics.put("folderName", "N/A");
                diagnostics.put("folderMimeType", "N/A");
            }

            diagnostics.put("ok", true);
            diagnostics.put("reason", "Configuración Drive válida");
            return diagnostics;
        } catch (Exception ex) {
            diagnostics.put("ok", false);
            diagnostics.put("reason", ex.getMessage());
            diagnostics.put("errorType", ex.getClass().getSimpleName());
            return diagnostics;
        }
    }

    public String uploadFile(String fileName, String contentType, byte[] bytes) throws IOException {
        return uploadFileToFolder(fileName, contentType, bytes, folderId);
    }

    public String uploadFileToFolder(String fileName, String contentType, byte[] bytes, String targetFolderId) throws IOException {
        if (!isEnabled()) {
            throw new IllegalStateException("Google Drive storage no está habilitado");
        }

        try {
            Drive drive = getDriveClient();
            com.google.api.services.drive.model.File fileMetadata = new com.google.api.services.drive.model.File();
            fileMetadata.setName((fileName == null || fileName.isBlank()) ? "documento.bin" : fileName);
            if (targetFolderId != null && !targetFolderId.isBlank()) {
                fileMetadata.setParents(Collections.singletonList(targetFolderId.trim()));
            }

            String mimeType = (contentType == null || contentType.isBlank())
                    ? "application/octet-stream"
                    : contentType;

            ByteArrayContent mediaContent = new ByteArrayContent(mimeType, bytes);
            com.google.api.services.drive.model.File created = drive.files()
                    .create(fileMetadata, mediaContent)
                    .setSupportsAllDrives(true)
                    .setFields("id")
                    .execute();

            return created.getId();
        } catch (GeneralSecurityException e) {
            throw new IOException("No fue posible inicializar cliente de Google Drive", e);
        }
    }

    public String createSolicitudFolderByDocumento(String numeroDocumento, int year) throws IOException {
        if (!isEnabled()) {
            throw new IllegalStateException("Google Drive storage no está habilitado");
        }

        String numeroNormalizado = normalizarNombreBase(numeroDocumento);
        String nombreBase = numeroNormalizado.isBlank() ? "solicitud" : numeroNormalizado;

        try {
            Drive drive = getDriveClient();

            if (!folderExistsByName(drive, nombreBase)) {
                return createFolder(drive, nombreBase);
            }

            String prefijo = nombreBase + "-" + year + "-";
            int maxSecuencia = findMaxSecuenciaByPrefijo(drive, prefijo);
            String nombreFinal = prefijo + (maxSecuencia + 1);
            return createFolder(drive, nombreFinal);
        } catch (GeneralSecurityException e) {
            throw new IOException("No fue posible inicializar cliente de Google Drive", e);
        }
    }

    private String createFolder(Drive drive, String folderName) throws IOException {
        com.google.api.services.drive.model.File folderMetadata = new com.google.api.services.drive.model.File();
        folderMetadata.setName(folderName);
        folderMetadata.setMimeType("application/vnd.google-apps.folder");

        if (folderId != null && !folderId.isBlank()) {
            folderMetadata.setParents(Collections.singletonList(folderId.trim()));
        }

        com.google.api.services.drive.model.File createdFolder = drive.files()
                .create(folderMetadata)
            .setSupportsAllDrives(true)
                .setFields("id")
                .execute();
        return createdFolder.getId();
    }

    private boolean folderExistsByName(Drive drive, String folderName) throws IOException {
        String query = baseFolderQuery()
                + " and mimeType = 'application/vnd.google-apps.folder'"
                + " and name = '" + escapeQueryValue(folderName) + "'";

        com.google.api.services.drive.model.FileList files = drive.files()
                .list()
                .setQ(query)
                .setPageSize(1)
            .setIncludeItemsFromAllDrives(true)
            .setSupportsAllDrives(true)
                .setFields("files(id)")
                .execute();

        return files.getFiles() != null && !files.getFiles().isEmpty();
    }

    private int findMaxSecuenciaByPrefijo(Drive drive, String prefijo) throws IOException {
        String query = baseFolderQuery()
                + " and mimeType = 'application/vnd.google-apps.folder'"
                + " and name contains '" + escapeQueryValue(prefijo) + "'";

        com.google.api.services.drive.model.FileList files = drive.files()
                .list()
                .setQ(query)
                .setPageSize(200)
            .setIncludeItemsFromAllDrives(true)
            .setSupportsAllDrives(true)
                .setFields("files(name)")
                .execute();

        Pattern pattern = Pattern.compile(Pattern.quote(prefijo) + "(\\d+)$");
        int max = 0;
        if (files.getFiles() == null) {
            return max;
        }

        for (com.google.api.services.drive.model.File file : files.getFiles()) {
            if (file.getName() == null) {
                continue;
            }
            Matcher matcher = pattern.matcher(file.getName());
            if (matcher.find()) {
                int current = Integer.parseInt(matcher.group(1));
                if (current > max) {
                    max = current;
                }
            }
        }
        return max;
    }

    private String baseFolderQuery() {
        String parentClause;
        if (folderId != null && !folderId.isBlank()) {
            parentClause = "'" + escapeQueryValue(folderId.trim()) + "' in parents";
        } else {
            parentClause = "'root' in parents";
        }
        return "trashed = false and " + parentClause;
    }

    private String normalizarNombreBase(String input) {
        if (input == null) {
            return "";
        }
        return input.trim().replaceAll("[^A-Za-z0-9]", "");
    }

    private String escapeQueryValue(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("'", "\\'");
    }

    public byte[] downloadFile(String fileId) throws IOException {
        if (!isEnabled()) {
            throw new IllegalStateException("Google Drive storage no está habilitado");
        }

        try {
            Drive drive = getDriveClient();
            try (InputStream inputStream = drive.files().get(fileId).setSupportsAllDrives(true).executeMediaAsInputStream()) {
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

            byte[] credentialsBytes = decodeCredentialsBytes();
            try (InputStream credentialsStream = new ByteArrayInputStream(credentialsBytes)) {
                GoogleCredentials credentials = GoogleCredentials.fromStream(credentialsStream)
                    .createScoped(Collections.singleton(DriveScopes.DRIVE));

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

    private byte[] decodeCredentialsBytes() {
        if (credentialsJsonBase64 == null || credentialsJsonBase64.isBlank()) {
            throw new IllegalStateException("Credenciales de Drive vacías");
        }

        String raw = credentialsJsonBase64.trim();
        if (raw.startsWith("{") && raw.endsWith("}")) {
            return raw.getBytes(StandardCharsets.UTF_8);
        }

        String normalizado = raw.replaceAll("\\s+", "");
        try {
            return Base64.getDecoder().decode(normalizado);
        } catch (IllegalArgumentException ex) {
            try {
                return Base64.getUrlDecoder().decode(normalizado);
            } catch (IllegalArgumentException ex2) {
                throw new IllegalStateException("Formato inválido en credenciales de Drive (se espera base64 o JSON crudo)");
            }
        }
    }

    private String extraerClientEmail(String jsonText) {
        if (jsonText == null || jsonText.isBlank()) {
            return "N/A";
        }
        Pattern pattern = Pattern.compile("\\\"client_email\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
        Matcher matcher = pattern.matcher(jsonText);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "N/A";
    }
}
