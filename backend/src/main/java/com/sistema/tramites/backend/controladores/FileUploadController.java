package com.sistema.tramites.backend.controladores;

import com.sistema.tramites.backend.*;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/tramites")
public class FileUploadController {

    private static final String DRIVE_PREFIX = "drive:";

    private final TramiteRepository tramiteRepository;
    private final UsuarioRepository usuarioRepository;
    private final EmailService emailService;
    private final DriveStorageService driveStorageService;
    private final AuditoriaTramiteService auditoriaTramiteService;
    private final NotificacionUsuarioService notificacionUsuarioService;

    public FileUploadController(TramiteRepository tramiteRepository,
                                UsuarioRepository usuarioRepository,
                                EmailService emailService,
                                DriveStorageService driveStorageService,
                                AuditoriaTramiteService auditoriaTramiteService,
                                NotificacionUsuarioService notificacionUsuarioService) {
        this.tramiteRepository = tramiteRepository;
        this.usuarioRepository = usuarioRepository;
        this.emailService = emailService;
        this.driveStorageService = driveStorageService;
        this.auditoriaTramiteService = auditoriaTramiteService;
        this.notificacionUsuarioService = notificacionUsuarioService;
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
            byte[] contenido = file.getBytes();
            String driveId = null;
            if (driveStorageService.isEnabled()) {
                String driveFolderId = obtenerOCrearCarpetaDrive(tramite);
                driveId = driveStorageService.uploadFileToFolder(file.getOriginalFilename(), contentType, contenido, driveFolderId);
            }

            switch (tipo.toLowerCase()) {
                case "sisben":
                    tramite.setContenidoCertificadoSisben(driveId != null ? null : contenido);
                    tramite.setNombreArchivoSisben(file.getOriginalFilename());
                    tramite.setTipoContenidoSisben(contentType);
                    tramite.setRuta_certificado_sisben(driveId != null ? DRIVE_PREFIX + driveId : null);
                    break;
                case "electoral":
                    tramite.setContenidoCertificadoElectoral(driveId != null ? null : contenido);
                    tramite.setNombreArchivoElectoral(file.getOriginalFilename());
                    tramite.setTipoContenidoElectoral(contentType);
                    tramite.setRuta_certificado_electoral(driveId != null ? DRIVE_PREFIX + driveId : null);
                    break;
                case "residencia":
                    tramite.setContenidoDocumentoResidencia(driveId != null ? null : contenido);
                    tramite.setNombreArchivoResidencia(file.getOriginalFilename());
                    tramite.setTipoContenidoResidencia(contentType);
                    tramite.setRuta_certificado(driveId != null ? DRIVE_PREFIX + driveId : null);
                    break;
                case "identidad":
                    tramite.setContenidoDocumentoIdentidad(driveId != null ? null : contenido);
                    tramite.setNombreArchivoIdentidad(file.getOriginalFilename());
                    tramite.setTipoContenidoIdentidad(contentType);
                    tramite.setRuta_documento_identidad(driveId != null ? DRIVE_PREFIX + driveId : null);
                    break;
                case "solicitud":
                    tramite.setContenidoDocumentoSolicitud(driveId != null ? null : contenido);
                    tramite.setNombreArchivoSolicitud(file.getOriginalFilename());
                    tramite.setTipoContenidoSolicitud(contentType);
                    tramite.setRuta_documento_solicitud(driveId != null ? DRIVE_PREFIX + driveId : null);
                    break;
                default:
                    return ResponseEntity.badRequest().body("❌ Tipo de documento no válido");
            }

                Tramite actualizado = tramiteRepository.save(tramite);

                String accion = "DOCUMENTO_CARGADO_" + tipo.toUpperCase(Locale.ROOT);
                String almacenamiento = driveId != null ? "DRIVE" : "BD";
                auditoriaTramiteService.registrarEvento(
                    actualizado.getId(),
                    null,
                    accion,
                    "Carga de " + tipo + " en " + almacenamiento + " para radicado " + actualizado.getNumeroRadicado(),
                    actualizado.getEstado(),
                    actualizado.getEstado()
                );

            return ResponseEntity.ok(new UploadResponseDTO(
                    true,
                    "✅ Archivo " + tipo + " cargado exitosamente",
                    actualizado.getId(),
                    driveId != null ? "DRIVE" : "BD",
                    actualizado.getDriveFolderId(),
                    driveId
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
            @PathVariable String tipo,
            @RequestParam(value = "accion", required = false) String accion,
            @RequestHeader(value = "X-Username", required = false) String usernameHeader,
            @RequestHeader(value = "X-Admin-Username", required = false) String adminUsernameHeader) {
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
            String driveFileId = null;

            switch (tipo.toLowerCase()) {
                case "sisben":
                    contenido = tramite.getContenidoCertificadoSisben();
                    nombreArchivo = tramite.getNombreArchivoSisben();
                    contentType = tramite.getTipoContenidoSisben();
                    driveFileId = extraerDriveFileId(tramite.getRuta_certificado_sisben());
                    break;
                case "electoral":
                    contenido = tramite.getContenidoCertificadoElectoral();
                    nombreArchivo = tramite.getNombreArchivoElectoral();
                    contentType = tramite.getTipoContenidoElectoral();
                    driveFileId = extraerDriveFileId(tramite.getRuta_certificado_electoral());
                    break;
                case "residencia":
                case "jac":
                    contenido = tramite.getContenidoDocumentoResidencia();
                    nombreArchivo = tramite.getNombreArchivoResidencia();
                    contentType = tramite.getTipoContenidoResidencia();
                    driveFileId = extraerDriveFileId(tramite.getRuta_certificado());
                    break;
                case "identidad":
                    contenido = tramite.getContenidoDocumentoIdentidad();
                    nombreArchivo = tramite.getNombreArchivoIdentidad();
                    contentType = tramite.getTipoContenidoIdentidad();
                    driveFileId = extraerDriveFileId(tramite.getRuta_documento_identidad());
                    break;
                case "solicitud":
                    contenido = tramite.getContenidoDocumentoSolicitud();
                    nombreArchivo = tramite.getNombreArchivoSolicitud();
                    contentType = tramite.getTipoContenidoSolicitud();
                    driveFileId = extraerDriveFileId(tramite.getRuta_documento_solicitud());
                    break;
                default:
                    return ResponseEntity.badRequest().body("❌ Tipo de documento no válido");
            }

            if (driveFileId != null && driveStorageService.isEnabled()) {
                contenido = driveStorageService.downloadFile(driveFileId);
            }

            if (contenido == null || contenido.length == 0) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body("❌ No hay archivo de " + tipo + " cargado");
            }

                Long usuarioId = resolverUsuarioIdPorHeaders(usernameHeader, adminUsernameHeader);
                String tipoNormalizado = (tipo == null ? "documento" : tipo.trim().toUpperCase(Locale.ROOT));
                String accionAuditoria = resolverAccionDocumento("DOCUMENTO", tipoNormalizado, accion);
                String almacenamiento = driveFileId != null ? "DRIVE" : "BD";
                auditoriaTramiteService.registrarEvento(
                    tramite.getId(),
                    usuarioId,
                    accionAuditoria,
                    "Acceso a " + tipoNormalizado + " en " + almacenamiento + " (archivo: " + (nombreArchivo == null ? "N/A" : nombreArchivo) + ")",
                    tramite.getEstado(),
                    tramite.getEstado()
                );

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
    public ResponseEntity<?> verificarDocumentos(
            @PathVariable Long id,
            @RequestHeader(value = "X-Username", required = false) String usernameHeader,
            @RequestHeader(value = "X-Admin-Username", required = false) String adminUsernameHeader) {
        try {
            Optional<Tramite> tramiteOpt = tramiteRepository.findById(id);
            if (tramiteOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body("❌ Trámite no encontrado");
            }

            Tramite tramite = tramiteOpt.get();
            VerificacionDocumentosDTO verificacion = new VerificacionDocumentosDTO();

            // Verificar Identidad
                String rutaIdentidad = tramite.getRuta_documento_identidad();
            verificacion.identidad = new DocumentoStatusDTO(
                    tieneContenidoOStorage(tramite.getContenidoDocumentoIdentidad(), rutaIdentidad),
                    tramite.getNombreArchivoIdentidad(),
                    tramite.getTipoContenidoIdentidad(),
                    tramite.getContenidoDocumentoIdentidad() != null ? tramite.getContenidoDocumentoIdentidad().length : 0,
                    resolverAlmacenamiento(tramite.getContenidoDocumentoIdentidad(), rutaIdentidad),
                    extraerDriveFileId(rutaIdentidad)
            );

            // Verificar Solicitud
                String rutaSolicitud = tramite.getRuta_documento_solicitud();
            verificacion.solicitud = new DocumentoStatusDTO(
                    tieneContenidoOStorage(tramite.getContenidoDocumentoSolicitud(), rutaSolicitud),
                    tramite.getNombreArchivoSolicitud(),
                    tramite.getTipoContenidoSolicitud(),
                    tramite.getContenidoDocumentoSolicitud() != null ? tramite.getContenidoDocumentoSolicitud().length : 0,
                    resolverAlmacenamiento(tramite.getContenidoDocumentoSolicitud(), rutaSolicitud),
                    extraerDriveFileId(rutaSolicitud)
            );

            // Verificar SISBEN
                String rutaSisben = tramite.getRuta_certificado_sisben();
            verificacion.sisben = new DocumentoStatusDTO(
                    tieneContenidoOStorage(tramite.getContenidoCertificadoSisben(), rutaSisben),
                    tramite.getNombreArchivoSisben(),
                    tramite.getTipoContenidoSisben(),
                    tramite.getContenidoCertificadoSisben() != null ? tramite.getContenidoCertificadoSisben().length : 0,
                    resolverAlmacenamiento(tramite.getContenidoCertificadoSisben(), rutaSisben),
                    extraerDriveFileId(rutaSisben)
            );

            // Verificar Electoral
                String rutaElectoral = tramite.getRuta_certificado_electoral();
            verificacion.electoral = new DocumentoStatusDTO(
                    tieneContenidoOStorage(tramite.getContenidoCertificadoElectoral(), rutaElectoral),
                    tramite.getNombreArchivoElectoral(),
                    tramite.getTipoContenidoElectoral(),
                    tramite.getContenidoCertificadoElectoral() != null ? tramite.getContenidoCertificadoElectoral().length : 0,
                    resolverAlmacenamiento(tramite.getContenidoCertificadoElectoral(), rutaElectoral),
                    extraerDriveFileId(rutaElectoral)
            );

            // Verificar Residencia
                String rutaResidencia = tramite.getRuta_certificado();
            verificacion.residencia = new DocumentoStatusDTO(
                    tieneContenidoOStorage(tramite.getContenidoDocumentoResidencia(), rutaResidencia),
                    tramite.getNombreArchivoResidencia(),
                    tramite.getTipoContenidoResidencia(),
                    tramite.getContenidoDocumentoResidencia() != null ? tramite.getContenidoDocumentoResidencia().length : 0,
                    resolverAlmacenamiento(tramite.getContenidoDocumentoResidencia(), rutaResidencia),
                    extraerDriveFileId(rutaResidencia)
            );
                // Alias para compatibilidad con el frontend cuando el tipo de certificado es JAC
                verificacion.jac = verificacion.residencia;

            verificacion.tramiteId = tramite.getId();
            verificacion.driveHabilitado = driveStorageService.isEnabled();
            verificacion.driveFolderId = tramite.getDriveFolderId();
            String rutaCertificadoFinal = tramite.getRuta_certificado_final();
            String driveFileIdCertificadoFinal = extraerDriveFileId(rutaCertificadoFinal);
            boolean certificadoGeneradoEnBd = tieneContenido(tramite.getContenidoPdfGenerado());
            verificacion.certificadoGeneradoDisponible = certificadoGeneradoEnBd || driveFileIdCertificadoFinal != null;
            verificacion.certificadoGeneradoAlmacenamiento = driveFileIdCertificadoFinal != null
                    ? "DRIVE"
                    : (certificadoGeneradoEnBd ? "BD" : "NINGUNO");
            verificacion.certificadoGeneradoDriveFileId = driveFileIdCertificadoFinal;
            verificacion.certificadoGeneradoNombre = tramite.getNombrePdfGenerado();
            verificacion.rutaCertificadoFinal = rutaCertificadoFinal;
                String claveCertificado = resolverClaveCertificado(tramite.getTipo_certificado());
                verificacion.totalDocumentosCargados =
                    (tieneContenidoOStorage(tramite.getContenidoDocumentoIdentidad(), tramite.getRuta_documento_identidad()) ? 1 : 0) +
                    (tieneContenidoOStorage(tramite.getContenidoDocumentoSolicitud(), tramite.getRuta_documento_solicitud()) ? 1 : 0) +
                    (documentoCertificadoCargado(tramite, claveCertificado) ? 1 : 0);

            Long usuarioId = resolverUsuarioIdPorHeaders(usernameHeader, adminUsernameHeader);
            if (usuarioId != null) {
                auditoriaTramiteService.registrarEvento(
                        tramite.getId(),
                        usuarioId,
                        "DOCUMENTOS_CONSULTADOS",
                        "Consulta de estado documental del trámite",
                        tramite.getEstado(),
                        tramite.getEstado()
                );
            }

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
            boolean identidadCargada = tieneContenidoOStorage(tramite.getContenidoDocumentoIdentidad(), tramite.getRuta_documento_identidad());
            boolean solicitudCargada = tieneContenidoOStorage(tramite.getContenidoDocumentoSolicitud(), tramite.getRuta_documento_solicitud());
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
                    .map(email -> email.trim().toLowerCase(Locale.ROOT))
                    .distinct()
                    .toArray(String[]::new);

            if (correosAdmin.length == 0) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body("❌ Los administradores activos no tienen correos válidos");
            }

            String usernameVerificador = (request == null || request.username == null || request.username.isBlank())
                    ? "verificador"
                    : request.username.trim();
                Long usuarioVerificadorId = usuarioRepository.findByUsernameAndRolAndActivoTrue(usernameVerificador, RolUsuario.VERIFICADOR)
                    .map(Usuario::getId)
                    .orElse(null);

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

                    List<Long> adminIds = adminsActivos.stream()
                        .map(Usuario::getId)
                        .filter(java.util.Objects::nonNull)
                        .distinct()
                        .toList();

                    String mensajeNotificacion = "El verificador " + usernameVerificador
                        + " solicitó revisión documental del trámite " + tramite.getNumeroRadicado()
                        + ". Faltantes: " + detalleFaltantes;
                    String tipoNotificacion = faltantes.isEmpty() ? "INFO" : "WARNING";

                    notificacionUsuarioService.crearParaUsuarios(
                        adminIds,
                        tramite.getId(),
                        "Revisión documental solicitada",
                        mensajeNotificacion,
                        tipoNotificacion
                    );

                    auditoriaTramiteService.registrarEvento(
                        tramite.getId(),
                        usuarioVerificadorId,
                        "NOTIFICACION_ADMIN_DOCUMENTOS",
                        "Notificación a " + correosAdmin.length + " admin(s). Faltantes: " + detalleFaltantes,
                        tramite.getEstado(),
                        tramite.getEstado()
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

    @GetMapping("/drive-diagnostico")
    public ResponseEntity<?> diagnosticarDrive() {
        try {
            Map<String, Object> diagnostics = driveStorageService.getDiagnostics();
            boolean ok = Boolean.TRUE.equals(diagnostics.get("ok"));
            return ResponseEntity.status(ok ? HttpStatus.OK : HttpStatus.BAD_REQUEST).body(diagnostics);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("❌ Error al diagnosticar Drive: " + e.getMessage());
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
            return tieneContenidoOStorage(tramite.getContenidoCertificadoElectoral(), tramite.getRuta_certificado_electoral());
        }
        if ("residencia".equalsIgnoreCase(claveCertificado)) {
            return tieneContenidoOStorage(tramite.getContenidoDocumentoResidencia(), tramite.getRuta_certificado());
        }
        return tieneContenidoOStorage(tramite.getContenidoCertificadoSisben(), tramite.getRuta_certificado_sisben());
    }

    private boolean tieneContenido(byte[] contenido) {
        return contenido != null && contenido.length > 0;
    }

    private boolean tieneContenidoOStorage(byte[] contenido, String ruta) {
        return tieneContenido(contenido) || extraerDriveFileId(ruta) != null;
    }

    private String resolverAlmacenamiento(byte[] contenido, String ruta) {
        if (extraerDriveFileId(ruta) != null) {
            return "DRIVE";
        }
        if (tieneContenido(contenido)) {
            return "BD";
        }
        return "NINGUNO";
    }

    private String extraerDriveFileId(String ruta) {
        if (ruta == null) {
            return null;
        }
        String valor = ruta.trim();
        if (valor.startsWith(DRIVE_PREFIX) && valor.length() > DRIVE_PREFIX.length()) {
            return valor.substring(DRIVE_PREFIX.length());
        }
        return null;
    }

    private Long resolverUsuarioIdPorHeaders(String usernameHeader, String adminUsernameHeader) {
        String username = null;
        if (usernameHeader != null && !usernameHeader.isBlank()) {
            username = usernameHeader.trim();
        } else if (adminUsernameHeader != null && !adminUsernameHeader.isBlank()) {
            username = adminUsernameHeader.trim();
        }

        if (username == null || username.isBlank()) {
            return null;
        }

        return usuarioRepository.findByUsername(username)
                .map(Usuario::getId)
                .orElse(null);
    }

    private String resolverAccionDocumento(String prefijo, String tipo, String accion) {
        String accionNormalizada = accion == null ? "" : accion.trim().toLowerCase(Locale.ROOT);
        if ("ver".equals(accionNormalizada) || "visualizar".equals(accionNormalizada) || "open".equals(accionNormalizada)) {
            return prefijo + "_VISUALIZADO_" + tipo;
        }
        if ("descargar".equals(accionNormalizada) || "download".equals(accionNormalizada)) {
            return prefijo + "_DESCARGADO_" + tipo;
        }
        return prefijo + "_ACCESO_" + tipo;
    }

    private String obtenerOCrearCarpetaDrive(Tramite tramite) throws IOException {
        if (tramite.getDriveFolderId() != null && !tramite.getDriveFolderId().isBlank()) {
            return tramite.getDriveFolderId();
        }

        int anio = (tramite.getFechaRadicacion() != null)
                ? tramite.getFechaRadicacion().getYear()
                : Year.now().getValue();

        String baseIdentificacion = (tramite.getNumeroDocumento() != null && !tramite.getNumeroDocumento().isBlank())
                ? tramite.getNumeroDocumento()
                : (tramite.getNumeroRadicado() != null ? tramite.getNumeroRadicado() : "solicitud");

        String folderId = driveStorageService.createSolicitudFolderByDocumento(baseIdentificacion, anio);
        tramite.setDriveFolderId(folderId);
        tramiteRepository.save(tramite);
        return folderId;
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
        public String almacenamiento;
        public String driveFolderId;
        public String driveFileId;

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

    /**
     * DTO para estado de un documento
     */
    public static class DocumentoStatusDTO {
        public boolean cargado;
        public String nombreArchivo;
        public String tipoContenido;
        public long tamañoBytes;
        public String almacenamiento;
        public String driveFileId;

        public DocumentoStatusDTO(boolean cargado, String nombreArchivo, String tipoContenido, long tamañoBytes, String almacenamiento, String driveFileId) {
            this.cargado = cargado;
            this.nombreArchivo = nombreArchivo;
            this.tipoContenido = tipoContenido;
            this.tamañoBytes = tamañoBytes;
            this.almacenamiento = almacenamiento;
            this.driveFileId = driveFileId;
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
        public boolean driveHabilitado;
        public String driveFolderId;
        public boolean certificadoGeneradoDisponible;
        public String certificadoGeneradoAlmacenamiento;
        public String certificadoGeneradoDriveFileId;
        public String certificadoGeneradoNombre;
        public String rutaCertificadoFinal;
    }

    public static class NotificacionAdminRequestDTO {
        public String username;
        public String mensaje;
    }
}
