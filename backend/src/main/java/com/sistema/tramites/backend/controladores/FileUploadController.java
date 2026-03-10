package com.sistema.tramites.backend.controladores;

import com.sistema.tramites.backend.servicios.FileUploadService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/tramites/upload")
@Tag(name = "File Upload", description = "Endpoints para carga y descarga de documentos de trámites")
@CrossOrigin(origins = "*")
public class FileUploadController {

    @Autowired
    private FileUploadService fileUploadService;

    @PostMapping("/{id}")
    @Operation(summary = "Cargar un documento para un trámite específico")
    public ResponseEntity<?> uploadDocumento(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file,
            @RequestParam("tipo") String tipo) {
        return fileUploadService.uploadDocumento(id, file, tipo);
    }

    @GetMapping("/download/{id}")
    @Operation(summary = "Descargar un documento cargado")
    public ResponseEntity<?> descargarDocumento(
            @PathVariable Long id,
            @RequestParam("tipo") String tipo,
            @RequestParam(value = "accion", defaultValue = "descargar") String accion,
            @RequestHeader(value = "X-Username", required = false) String usernameHeader,
            @RequestHeader(value = "X-Admin-Username", required = false) String adminUsernameHeader) {
        return fileUploadService.descargarDocumento(id, tipo, accion, usernameHeader, adminUsernameHeader);
    }

    @GetMapping("/verificar/{id}")
    @Operation(summary = "Verificar el estado de los documentos de un trámite")
    public ResponseEntity<?> verificarDocumentos(
            @PathVariable Long id,
            @RequestHeader(value = "X-Username", required = false) String usernameHeader,
            @RequestHeader(value = "X-Admin-Username", required = false) String adminUsernameHeader) {
        return fileUploadService.verificarDocumentos(id, usernameHeader, adminUsernameHeader);
    }

    @PostMapping("/notificar-admin/{id}")
    public ResponseEntity<?> notificarAdminDocumentos(@PathVariable Long id, @RequestBody Object request) {
        return fileUploadService.notificarAdminDocumentos(id, request);
    }

    @GetMapping("/diagnostico-drive")
    public ResponseEntity<?> diagnosticarDrive() {
        return fileUploadService.diagnosticarDrive();
    }

    // --- DTOs ---

    public static class DocumentoStatusDTO {
        public boolean cargado;
        public String nombre;
        public String contentType;
        public long tamano;
        public String almacenamiento;
        public String driveFileId;

        public DocumentoStatusDTO() {}

        public DocumentoStatusDTO(boolean cargado, String nombre, String contentType, long tamano, String almacenamiento, String driveFileId) {
            this.cargado = cargado;
            this.nombre = nombre;
            this.contentType = contentType;
            this.tamano = tamano;
            this.almacenamiento = almacenamiento;
            this.driveFileId = driveFileId;
        }
    }

    public static class VerificacionDocumentosDTO {
        public Long tramiteId;
        public DocumentoStatusDTO identidad;
        public DocumentoStatusDTO solicitud;
        public DocumentoStatusDTO sisben;
        public DocumentoStatusDTO electoral;
        public DocumentoStatusDTO residencia;
        public DocumentoStatusDTO jac;
        public boolean driveHabilitado;
        public String driveFolderId;
        public boolean certificadoGeneradoDisponible;
        public String certificadoGeneradoAlmacenamiento;
        public String certificadoGeneradoDriveFileId;
        public String certificadoGeneradoNombre;
        public String rutaCertificadoFinal;
        public int totalDocumentosCargados;
    }
}
