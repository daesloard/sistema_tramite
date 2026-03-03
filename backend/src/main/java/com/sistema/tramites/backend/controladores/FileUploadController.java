package com.sistema.tramites.backend.controladores;

import com.sistema.tramites.backend.*;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/tramites")
public class FileUploadController {

    private final TramiteRepository tramiteRepository;
    private final UsuarioRepository usuarioRepository;
    private final EmailService emailService;

    public FileUploadController(TramiteRepository tramiteRepository,
                                UsuarioRepository usuarioRepository,
                                EmailService emailService) {
        this.tramiteRepository = tramiteRepository;
        this.usuarioRepository = usuarioRepository;
        this.emailService = emailService;
    }

    /**
     * Subir documento SISBEN
     */
    @PostMapping("/{id}/upload-sisben")
    public ResponseEntity<?> uploadSisben(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file) {
        return uploadDocumento(id, file, "sisben");
    }

    /**
     * Subir documento Electoral
     */
    @PostMapping("/{id}/upload-electoral")
    public ResponseEntity<?> uploadElectoral(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file) {
        return uploadDocumento(id, file, "electoral");
    }

    /**
     * Subir documento Residencia
     */
    @PostMapping("/{id}/upload-residencia")
    public ResponseEntity<?> uploadResidencia(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file) {
        return uploadDocumento(id, file, "residencia");
    }

    /**
     * Subir documento de Identidad
     */
    @PostMapping("/{id}/upload-identidad")
    public ResponseEntity<?> uploadIdentidad(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file) {
        return uploadDocumento(id, file, "identidad");
    }

    /**
     * Subir documento de Solicitud
     */
    @PostMapping("/{id}/upload-solicitud")
    public ResponseEntity<?> uploadSolicitud(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file) {
        return uploadDocumento(id, file, "solicitud");
    }

    /**
     * Método genérico para subir documento
     */
    private ResponseEntity<?> uploadDocumento(Long id, MultipartFile file, String tipo) {
        try {
            // Validar que el archivo no esté vacío
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body("❌ El archivo está vacío");
            }

            // Validar tamaño máximo (10MB)
            long maxSize = 10 * 1024 * 1024;
            if (file.getSize() > maxSize) {
                return ResponseEntity.badRequest().body("❌ El archivo excede el tamaño máximo de 10MB");
            }

            // Validar tipo de archivo (solo PDF, JPG, PNG)
            String contentType = file.getContentType();
            if (!isValidFileType(contentType, file.getOriginalFilename())) {
                return ResponseEntity.badRequest()
                        .body("❌ Tipo de archivo no permitido. Solo se aceptan PDF, JPG, PNG");
            }

            // Buscar el trámite
            Optional<Tramite> tramiteOpt = tramiteRepository.findById(id);
            if (tramiteOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body("❌ Trámite no encontrado");
            }

            Tramite tramite = tramiteOpt.get();

            // Guardar el archivo según el tipo
            switch (tipo.toLowerCase()) {
                case "sisben":
                    tramite.setContenidoCertificadoSisben(file.getBytes());
                    tramite.setNombreArchivoSisben(file.getOriginalFilename());
                    tramite.setTipoContenidoSisben(contentType);
                    break;
                case "electoral":
                    tramite.setContenidoCertificadoElectoral(file.getBytes());
                    tramite.setNombreArchivoElectoral(file.getOriginalFilename());
                    tramite.setTipoContenidoElectoral(contentType);
                    break;
                case "residencia":
                    tramite.setContenidoDocumentoResidencia(file.getBytes());
                    tramite.setNombreArchivoResidencia(file.getOriginalFilename());
                    tramite.setTipoContenidoResidencia(contentType);
                    break;
                case "identidad":
                    tramite.setContenidoDocumentoIdentidad(file.getBytes());
                    tramite.setNombreArchivoIdentidad(file.getOriginalFilename());
                    tramite.setTipoContenidoIdentidad(contentType);
                    break;
                case "solicitud":
                    tramite.setContenidoDocumentoSolicitud(file.getBytes());
                    tramite.setNombreArchivoSolicitud(file.getOriginalFilename());
                    tramite.setTipoContenidoSolicitud(contentType);
                    break;
                default:
                    return ResponseEntity.badRequest().body("❌ Tipo de documento no válido");
            }

            tramiteRepository.save(tramite);

            return ResponseEntity.ok(new UploadResponseDTO(
                    true,
                    "✅ Archivo " + tipo + " cargado exitosamente",
                    tramite.getId()
            ));

        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("❌ Error al guardar el archivo: " + e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("❌ Error interno: " + e.getMessage());
        }
    }

    /**
     * Descargar documento
     */
    @GetMapping("/{id}/descargar/{tipo}")
    public ResponseEntity<?> descargarDocumento(
            @PathVariable Long id,
            @PathVariable String tipo) {
        try {
            Optional<Tramite> tramiteOpt = tramiteRepository.findById(id);
            if (tramiteOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body("❌ Trámite no encontrado");
            }

            Tramite tramite = tramiteOpt.get();
            byte[] contenido = null;
            String nombreArchivo = null;
            String contentType = null;

            switch (tipo.toLowerCase()) {
                case "sisben":
                    contenido = tramite.getContenidoCertificadoSisben();
                    nombreArchivo = tramite.getNombreArchivoSisben();
                    contentType = tramite.getTipoContenidoSisben();
                    break;
                case "electoral":
                    contenido = tramite.getContenidoCertificadoElectoral();
                    nombreArchivo = tramite.getNombreArchivoElectoral();
                    contentType = tramite.getTipoContenidoElectoral();
                    break;
                case "residencia":
                case "jac":
                    contenido = tramite.getContenidoDocumentoResidencia();
                    nombreArchivo = tramite.getNombreArchivoResidencia();
                    contentType = tramite.getTipoContenidoResidencia();
                    break;
                case "identidad":
                    contenido = tramite.getContenidoDocumentoIdentidad();
                    nombreArchivo = tramite.getNombreArchivoIdentidad();
                    contentType = tramite.getTipoContenidoIdentidad();
                    break;
                case "solicitud":
                    contenido = tramite.getContenidoDocumentoSolicitud();
                    nombreArchivo = tramite.getNombreArchivoSolicitud();
                    contentType = tramite.getTipoContenidoSolicitud();
                    break;
                default:
                    return ResponseEntity.badRequest().body("❌ Tipo de documento no válido");
            }

            if (contenido == null || contenido.length == 0) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body("❌ No hay archivo de " + tipo + " cargado");
            }

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + nombreArchivo + "\"")
                    .header(HttpHeaders.CONTENT_TYPE, contentType)
                    .body(contenido);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("❌ Error al descargar: " + e.getMessage());
        }
    }

    /**
     * Verificar estado de documentos de un trámite
     */
    @GetMapping("/{id}/verificar-documentos")
    public ResponseEntity<?> verificarDocumentos(@PathVariable Long id) {
        try {
            Optional<Tramite> tramiteOpt = tramiteRepository.findById(id);
            if (tramiteOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body("❌ Trámite no encontrado");
            }

            Tramite tramite = tramiteOpt.get();
            VerificacionDocumentosDTO verificacion = new VerificacionDocumentosDTO();

            // Verificar Identidad
            verificacion.identidad = new DocumentoStatusDTO(
                    tramite.getContenidoDocumentoIdentidad() != null,
                    tramite.getNombreArchivoIdentidad(),
                    tramite.getTipoContenidoIdentidad(),
                    tramite.getContenidoDocumentoIdentidad() != null ? tramite.getContenidoDocumentoIdentidad().length : 0
            );

            // Verificar Solicitud
            verificacion.solicitud = new DocumentoStatusDTO(
                    tramite.getContenidoDocumentoSolicitud() != null,
                    tramite.getNombreArchivoSolicitud(),
                    tramite.getTipoContenidoSolicitud(),
                    tramite.getContenidoDocumentoSolicitud() != null ? tramite.getContenidoDocumentoSolicitud().length : 0
            );

            // Verificar SISBEN
            verificacion.sisben = new DocumentoStatusDTO(
                    tramite.getContenidoCertificadoSisben() != null,
                    tramite.getNombreArchivoSisben(),
                    tramite.getTipoContenidoSisben(),
                    tramite.getContenidoCertificadoSisben() != null ? tramite.getContenidoCertificadoSisben().length : 0
            );

            // Verificar Electoral
            verificacion.electoral = new DocumentoStatusDTO(
                    tramite.getContenidoCertificadoElectoral() != null,
                    tramite.getNombreArchivoElectoral(),
                    tramite.getTipoContenidoElectoral(),
                    tramite.getContenidoCertificadoElectoral() != null ? tramite.getContenidoCertificadoElectoral().length : 0
            );

            // Verificar Residencia
            verificacion.residencia = new DocumentoStatusDTO(
                    tramite.getContenidoDocumentoResidencia() != null,
                    tramite.getNombreArchivoResidencia(),
                    tramite.getTipoContenidoResidencia(),
                    tramite.getContenidoDocumentoResidencia() != null ? tramite.getContenidoDocumentoResidencia().length : 0
            );
                // Alias para compatibilidad con el frontend cuando el tipo de certificado es JAC
                verificacion.jac = verificacion.residencia;

            verificacion.tramiteId = tramite.getId();
                String claveCertificado = resolverClaveCertificado(tramite.getTipo_certificado());
                verificacion.totalDocumentosCargados =
                    (tieneContenido(tramite.getContenidoDocumentoIdentidad()) ? 1 : 0) +
                    (tieneContenido(tramite.getContenidoDocumentoSolicitud()) ? 1 : 0) +
                    (documentoCertificadoCargado(tramite, claveCertificado) ? 1 : 0);

            return ResponseEntity.ok(verificacion);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("❌ Error al verificar documentos: " + e.getMessage());
        }
    }

    @PostMapping("/{id}/notificar-admin-documentos")
    public ResponseEntity<?> notificarAdminDocumentos(
            @PathVariable Long id,
            @RequestBody(required = false) NotificacionAdminRequestDTO request) {
        try {
            Optional<Tramite> tramiteOpt = tramiteRepository.findById(id);
            if (tramiteOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body("❌ Trámite no encontrado");
            }

            Tramite tramite = tramiteOpt.get();
            List<Usuario> adminsActivos = usuarioRepository.findAllByRolAndActivoTrue(RolUsuario.ADMINISTRADOR);
            if (adminsActivos.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body("❌ No hay administradores activos para notificar");
            }

            String claveCertificado = resolverClaveCertificado(tramite.getTipo_certificado());
            boolean identidadCargada = tieneContenido(tramite.getContenidoDocumentoIdentidad());
            boolean solicitudCargada = tieneContenido(tramite.getContenidoDocumentoSolicitud());
            boolean certificadoCargado = documentoCertificadoCargado(tramite, claveCertificado);

            List<String> faltantes = new ArrayList<>();
            if (!identidadCargada) {
                faltantes.add("Documento de Identidad");
            }
            if (!solicitudCargada) {
                faltantes.add("Documento de Solicitud");
            }
            if (!certificadoCargado) {
                faltantes.add("Certificado " + claveCertificado.toUpperCase());
            }

            int documentosRequeridos = 3;
            int documentosCargados = documentosRequeridos - faltantes.size();

            String[] correosAdmin = adminsActivos.stream()
                    .map(Usuario::getEmail)
                    .filter(email -> email != null && !email.isBlank())
                    .distinct()
                    .toArray(String[]::new);

            if (correosAdmin.length == 0) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body("❌ Los administradores activos no tienen correos válidos");
            }

            String usernameVerificador = (request == null || request.username == null || request.username.isBlank())
                    ? "verificador"
                    : request.username.trim();

            String detalleFaltantes = faltantes.isEmpty() ? "NINGUNO" : String.join(", ", faltantes);

            emailService.enviarNotificacionAdminRevisionDocumentos(
                    correosAdmin,
                    tramite.getNumeroRadicado(),
                    tramite.getNombreSolicitante(),
                    tramite.getTipo_certificado(),
                    usernameVerificador,
                    documentosCargados,
                    documentosRequeridos,
                    detalleFaltantes,
                    request != null ? request.mensaje : null
            );

            var respuesta = new java.util.HashMap<String, Object>();
            respuesta.put("ok", true);
            respuesta.put("tramiteId", tramite.getId());
            respuesta.put("numeroRadicado", tramite.getNumeroRadicado());
            respuesta.put("enviadoA", correosAdmin.length);
            respuesta.put("documentosCargados", documentosCargados);
            respuesta.put("documentosRequeridos", documentosRequeridos);
            respuesta.put("faltantes", faltantes);

            return ResponseEntity.ok(respuesta);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("❌ Error al notificar administradores: " + e.getMessage());
        }
    }

    private String resolverClaveCertificado(String tipoCertificado) {
        String tipo = tipoCertificado == null ? "" : tipoCertificado.trim().toLowerCase();
        if ("electoral".equals(tipo)) {
            return "electoral";
        }
        if ("jac".equals(tipo)) {
            return "residencia";
        }
        return "sisben";
    }

    private boolean documentoCertificadoCargado(Tramite tramite, String claveCertificado) {
        if ("electoral".equalsIgnoreCase(claveCertificado)) {
            return tieneContenido(tramite.getContenidoCertificadoElectoral());
        }
        if ("residencia".equalsIgnoreCase(claveCertificado)) {
            return tieneContenido(tramite.getContenidoDocumentoResidencia());
        }
        return tieneContenido(tramite.getContenidoCertificadoSisben());
    }

    private boolean tieneContenido(byte[] contenido) {
        return contenido != null && contenido.length > 0;
    }

    /**
     * Validar tipo de archivo
     */
    private boolean isValidFileType(String contentType, String fileName) {
        if (contentType != null) {
            if (contentType.equals("application/pdf") ||
                contentType.equals("image/jpeg") ||
                contentType.equals("image/png") ||
                contentType.equals("image/jpg") ||
                contentType.equals("application/octet-stream")) {
                return true;
            }
        }

        if (fileName == null) return false;
        String lower = fileName.toLowerCase();
        return lower.endsWith(".pdf") ||
               lower.endsWith(".jpg") ||
               lower.endsWith(".jpeg") ||
               lower.endsWith(".png");
    }

    /**
     * DTO para respuesta de upload
     */
    public static class UploadResponseDTO {
        public boolean success;
        public String message;
        public Long tramiteId;

        public UploadResponseDTO(boolean success, String message, Long tramiteId) {
            this.success = success;
            this.message = message;
            this.tramiteId = tramiteId;
        }
    }

    /**
     * DTO para estado de un documento
     */
    public static class DocumentoStatusDTO {
        public boolean cargado;
        public String nombreArchivo;
        public String tipoContenido;
        public long tamañoBytes;

        public DocumentoStatusDTO(boolean cargado, String nombreArchivo, String tipoContenido, long tamañoBytes) {
            this.cargado = cargado;
            this.nombreArchivo = nombreArchivo;
            this.tipoContenido = tipoContenido;
            this.tamañoBytes = tamañoBytes;
        }
    }

    /**
     * DTO para verificación de todos los documentos de un trámite
     */
    public static class VerificacionDocumentosDTO {
        public Long tramiteId;
        public DocumentoStatusDTO identidad;
        public DocumentoStatusDTO solicitud;
        public DocumentoStatusDTO sisben;
        public DocumentoStatusDTO electoral;
        public DocumentoStatusDTO residencia;
        public DocumentoStatusDTO jac;
        public int totalDocumentosCargados;
    }

    public static class NotificacionAdminRequestDTO {
        public String username;
        public String mensaje;
    }
}
