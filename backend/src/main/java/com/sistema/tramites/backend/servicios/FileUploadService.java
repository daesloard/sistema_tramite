package com.sistema.tramites.backend.servicios;

import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.ResponseEntity;

public interface FileUploadService {
    ResponseEntity<?> uploadDocumento(Long id, MultipartFile file, String tipo);
    ResponseEntity<?> descargarDocumento(Long id, String tipo, String accion, String usernameHeader, String adminUsernameHeader);
    ResponseEntity<?> verificarDocumentos(Long id, String usernameHeader, String adminUsernameHeader);
    ResponseEntity<?> notificarAdminDocumentos(Long id, Object request);
    ResponseEntity<?> diagnosticarDrive();
}
