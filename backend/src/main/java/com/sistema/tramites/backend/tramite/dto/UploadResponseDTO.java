package com.sistema.tramites.backend.tramite.dto;

public class UploadResponseDTO {
    public boolean success;
    public String message;
    public Long tramiteId;
    public String almacenamiento;
    public String driveFolderId;
    public String driveFileId;

    public UploadResponseDTO() {}

    public UploadResponseDTO(boolean success, String message, Long tramiteId,
                             String almacenamiento, String driveFolderId, String driveFileId) {
        this.success = success;
        this.message = message;
        this.tramiteId = tramiteId;
        this.almacenamiento = almacenamiento;
        this.driveFolderId = driveFolderId;
        this.driveFileId = driveFileId;
    }
}