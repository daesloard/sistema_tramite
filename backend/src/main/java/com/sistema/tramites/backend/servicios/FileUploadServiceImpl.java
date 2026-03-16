package com.sistema.tramites.backend.servicios;


import com.sistema.tramites.backend.tramite.dto.UploadResponseDTO;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.Locale;
import java.util.Optional;
import java.util.Objects;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import org.springframework.beans.factory.annotation.Autowired;
import com.sistema.tramites.backend.auditoria.AuditoriaTramiteService;
import com.sistema.tramites.backend.documento.DriveStorageService;
import com.sistema.tramites.backend.notificacion.NotificacionUsuarioService;
import com.sistema.tramites.backend.tramite.Tramite;
import com.sistema.tramites.backend.tramite.TramiteRepository;
import com.sistema.tramites.backend.usuario.Usuario;
import com.sistema.tramites.backend.usuario.RolUsuario;
import com.sistema.tramites.backend.usuario.UsuarioRepository;
import com.sistema.tramites.backend.util.EmailService;
import com.sistema.tramites.backend.controladores.FileUploadController.VerificacionDocumentosDTO;
import com.sistema.tramites.backend.controladores.FileUploadController.DocumentoStatusDTO;

@Service
public class FileUploadServiceImpl implements FileUploadService {
    @Autowired
    private TramiteRepository tramiteRepository;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private EmailService emailService;
    @Autowired
    private DriveStorageService driveStorageService;
    @Autowired
    private AuditoriaTramiteService auditoriaTramiteService;
    @Autowired
    private NotificacionUsuarioService notificacionUsuarioService;

    @Override
    public ResponseEntity<?> uploadDocumento(Long id, MultipartFile file, String tipo) {
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
                try {
                    String driveFolderId = driveStorageService.obtenerOCrearCarpetaDrive(tramite);
                    driveId = driveStorageService.uploadFileToFolder(file.getOriginalFilename(), contentType, contenido, driveFolderId);
                } catch (Exception e) {
                    // Loguear pero no fallar, se guardará en BD por defecto al ser driveId == null
                    System.err.println("Falla en Google Drive (se usará BD): " + e.getMessage());
                }
            }

            switch (tipo.toLowerCase(Locale.ROOT)) {
                case "sisben":
                    tramite.setContenidoCertificadoSisben(driveId != null ? null : contenido);
                    tramite.setNombreArchivoSisben(file.getOriginalFilename());
                    tramite.setTipoContenidoSisben(contentType);
                    tramite.setRuta_certificado_sisben(driveId != null ? "drive:" + driveId : null);
                    break;
                case "electoral":
                    tramite.setContenidoCertificadoElectoral(driveId != null ? null : contenido);
                    tramite.setNombreArchivoElectoral(file.getOriginalFilename());
                    tramite.setTipoContenidoElectoral(contentType);
                    tramite.setRuta_certificado_electoral(driveId != null ? "drive:" + driveId : null);
                    break;
                case "residencia":
                    tramite.setContenidoDocumentoResidencia(driveId != null ? null : contenido);
                    tramite.setNombreArchivoResidencia(file.getOriginalFilename());
                    tramite.setTipoContenidoResidencia(contentType);
                    tramite.setRuta_certificado(driveId != null ? "drive:" + driveId : null);
                    break;
                case "identidad":
                    tramite.setContenidoDocumentoIdentidad(driveId != null ? null : contenido);
                    tramite.setNombreArchivoIdentidad(file.getOriginalFilename());
                    tramite.setTipoContenidoIdentidad(contentType);
                    tramite.setRuta_documento_identidad(driveId != null ? "drive:" + driveId : null);
                    break;
                case "solicitud":
                    tramite.setContenidoDocumentoSolicitud(driveId != null ? null : contenido);
                    tramite.setNombreArchivoSolicitud(file.getOriginalFilename());
                    tramite.setTipoContenidoSolicitud(contentType);
                    tramite.setRuta_documento_solicitud(driveId != null ? "drive:" + driveId : null);
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

    @Override
    public ResponseEntity<?> descargarDocumento(Long id, String tipo, String accion, String usernameHeader, String adminUsernameHeader) {
        try {
            Optional<Tramite> tramiteOpt = tramiteRepository.findById(id);
            if (tramiteOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("❌ Trámite no encontrado");
            }

            Tramite tramite = tramiteOpt.get();
            byte[] contenido = null;
            String nombreArchivo = null;
            String contentType = null;
            String ruta = null;

            switch (tipo.toLowerCase(Locale.ROOT)) {
                case "sisben":
                    contenido = tramite.getContenidoCertificadoSisben();
                    nombreArchivo = tramite.getNombreArchivoSisben();
                    contentType = tramite.getTipoContenidoSisben();
                    ruta = tramite.getRuta_certificado_sisben();
                    break;
                case "electoral":
                    contenido = tramite.getContenidoCertificadoElectoral();
                    nombreArchivo = tramite.getNombreArchivoElectoral();
                    contentType = tramite.getTipoContenidoElectoral();
                    ruta = tramite.getRuta_certificado_electoral();
                    break;
                case "residencia":
                    contenido = tramite.getContenidoDocumentoResidencia();
                    nombreArchivo = tramite.getNombreArchivoResidencia();
                    contentType = tramite.getTipoContenidoResidencia();
                    ruta = tramite.getRuta_certificado();
                    break;
                case "identidad":
                    contenido = tramite.getContenidoDocumentoIdentidad();
                    nombreArchivo = tramite.getNombreArchivoIdentidad();
                    contentType = tramite.getTipoContenidoIdentidad();
                    ruta = tramite.getRuta_documento_identidad();
                    break;
                case "solicitud":
                    contenido = tramite.getContenidoDocumentoSolicitud();
                    nombreArchivo = tramite.getNombreArchivoSolicitud();
                    contentType = tramite.getTipoContenidoSolicitud();
                    ruta = tramite.getRuta_documento_solicitud();
                    break;
                default:
                    return ResponseEntity.badRequest().body("❌ Tipo de documento no válido");
            }

            if ((contenido == null || contenido.length == 0) && extraerDriveFileId(ruta) != null) {
                contenido = driveStorageService.downloadFile(extraerDriveFileId(ruta));
            }

            if (contenido == null || contenido.length == 0) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("❌ El documento no ha sido cargado");
            }

            Long usuarioId = resolverUsuarioIdPorHeaders(usernameHeader, adminUsernameHeader);
            String accionAuditoria = resolverAccionDocumento("DOCUMENTO", tipo.toUpperCase(), accion);
            auditoriaTramiteService.registrarEvento(
                tramite.getId(), usuarioId, accionAuditoria,
                "Acceso a " + tipo + " para radicado " + tramite.getNumeroRadicado(),
                tramite.getEstado(), tramite.getEstado()
            );

            return ResponseEntity.ok()
                    .header(org.springframework.http.HttpHeaders.CONTENT_TYPE, contentType)
                    .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nombreArchivo + "\"")
                    .body(contenido);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("❌ Error al descargar documento: " + e.getMessage());
        }
    }

    @Override
    public ResponseEntity<?> verificarDocumentos(Long id, String usernameHeader, String adminUsernameHeader) {
        try {
            Optional<Tramite> tramiteOpt = tramiteRepository.findById(id);
            if (tramiteOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("❌ Trámite no encontrado");
            }

            Tramite tramite = tramiteOpt.get();
            VerificacionDocumentosDTO verificacion = new VerificacionDocumentosDTO();
            
            String rutaIdentidad = tramite.getRuta_documento_identidad();
            byte[] cIdentidad = tramite.getContenidoDocumentoIdentidad();
            verificacion.identidad = new DocumentoStatusDTO(
                    tieneContenidoOStorage(cIdentidad, rutaIdentidad),
                    tramite.getNombreArchivoIdentidad(),
                    tramite.getTipoContenidoIdentidad(),
                    cIdentidad != null ? cIdentidad.length : 0,
                    resolverAlmacenamiento(cIdentidad, rutaIdentidad),
                    extraerDriveFileId(rutaIdentidad)
            );

            String rutaSolicitud = tramite.getRuta_documento_solicitud();
            byte[] cSolicitud = tramite.getContenidoDocumentoSolicitud();
            verificacion.solicitud = new DocumentoStatusDTO(
                    tieneContenidoOStorage(cSolicitud, rutaSolicitud),
                    tramite.getNombreArchivoSolicitud(),
                    tramite.getTipoContenidoSolicitud(),
                    cSolicitud != null ? cSolicitud.length : 0,
                    resolverAlmacenamiento(cSolicitud, rutaSolicitud),
                    extraerDriveFileId(rutaSolicitud)
            );

            String rutaSisben = tramite.getRuta_certificado_sisben();
            byte[] cSisben = tramite.getContenidoCertificadoSisben();
            verificacion.sisben = new DocumentoStatusDTO(
                    tieneContenidoOStorage(cSisben, rutaSisben),
                    tramite.getNombreArchivoSisben(),
                    tramite.getTipoContenidoSisben(),
                    cSisben != null ? cSisben.length : 0,
                    resolverAlmacenamiento(cSisben, rutaSisben),
                    extraerDriveFileId(rutaSisben)
            );

            String rutaElectoral = tramite.getRuta_certificado_electoral();
            byte[] cElectoral = tramite.getContenidoCertificadoElectoral();
            verificacion.electoral = new DocumentoStatusDTO(
                    tieneContenidoOStorage(cElectoral, rutaElectoral),
                    tramite.getNombreArchivoElectoral(),
                    tramite.getTipoContenidoElectoral(),
                    cElectoral != null ? cElectoral.length : 0,
                    resolverAlmacenamiento(cElectoral, rutaElectoral),
                    extraerDriveFileId(rutaElectoral)
            );

            String rutaResidencia = tramite.getRuta_certificado();
            byte[] cResidencia = tramite.getContenidoDocumentoResidencia();
            verificacion.residencia = new DocumentoStatusDTO(
                    tieneContenidoOStorage(cResidencia, rutaResidencia),
                    tramite.getNombreArchivoResidencia(),
                    tramite.getTipoContenidoResidencia(),
                    cResidencia != null ? cResidencia.length : 0,
                    resolverAlmacenamiento(cResidencia, rutaResidencia),
                    extraerDriveFileId(rutaResidencia)
            );
            verificacion.jac = verificacion.residencia;

            verificacion.tramiteId = tramite.getId();
            verificacion.driveHabilitado = driveStorageService.isEnabled();
            verificacion.driveFolderId = tramite.getDriveFolderId();
            String rutaCertificadoFinal = tramite.getRuta_certificado_final();
            String driveFileIdCertificadoFinal = extraerDriveFileId(rutaCertificadoFinal);
            boolean certificadoGeneradoEnBd = tieneContenido(tramite.getContenidoPdfGenerado());
            verificacion.certificadoGeneradoDisponible = certificadoGeneradoEnBd || driveFileIdCertificadoFinal != null;
            verificacion.certificadoGeneradoAlmacenamiento = driveFileIdCertificadoFinal != null ? "DRIVE" : (certificadoGeneradoEnBd ? "BD" : "NINGUNO");
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
                auditoriaTramiteService.registrarEvento(tramite.getId(), usuarioId, "DOCUMENTOS_CONSULTADOS", "Consulta documental", tramite.getEstado(), tramite.getEstado());
            }

            return ResponseEntity.ok(verificacion);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("❌ Error al verificar documentos: " + e.getMessage());
        }
    }

    @Override
    public ResponseEntity<?> notificarAdminDocumentos(Long id, Object requestBody) {
        try {
            Optional<Tramite> tramiteOpt = tramiteRepository.findById(id);
            if (tramiteOpt.isEmpty()) return ResponseEntity.status(HttpStatus.NOT_FOUND).body("❌ Trámite no encontrado");
            Tramite tramite = tramiteOpt.get();
            
            String username = "verificador";
            String mensaje = null;
            if (requestBody instanceof java.util.Map) {
                java.util.Map<?,?> map = (java.util.Map<?,?>) requestBody;
                if (map.get("username") != null) username = map.get("username").toString();
                if (map.get("mensaje") != null) mensaje = map.get("mensaje").toString();
            }

            List<Usuario> admins = usuarioRepository.findAllByRolAndActivoTrue(RolUsuario.ADMINISTRADOR);
            if (admins.isEmpty()) return ResponseEntity.status(HttpStatus.NOT_FOUND).body("❌ No hay administradores activos");

            String[] correos = admins.stream().map(Usuario::getEmail).filter(Objects::nonNull).toArray(String[]::new);
            
            String claveCertificado = resolverClaveCertificado(tramite.getTipo_certificado());
            List<String> faltantes = new ArrayList<>();
            if (!tieneContenidoOStorage(tramite.getContenidoDocumentoIdentidad(), tramite.getRuta_documento_identidad())) faltantes.add("Identidad");
            if (!tieneContenidoOStorage(tramite.getContenidoDocumentoSolicitud(), tramite.getRuta_documento_solicitud())) faltantes.add("Solicitud");
            if (!documentoCertificadoCargado(tramite, claveCertificado)) faltantes.add("Certificado " + claveCertificado);

            emailService.enviarNotificacionAdminRevisionDocumentos(
                correos, tramite.getNumeroRadicado(), tramite.getNombreSolicitante(),
                tramite.getTipo_certificado(), username, 3 - faltantes.size(), 3, String.join(", ", faltantes), mensaje
            );

            return ResponseEntity.ok(java.util.Map.of("ok", true, "enviadoA", correos.length));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("❌ Error: " + e.getMessage());
        }
    }

    @Override
    public ResponseEntity<?> diagnosticarDrive() {
        try {
            return ResponseEntity.ok(driveStorageService.getDiagnostics());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("❌ Error Drive: " + e.getMessage());
        }
    }

    private boolean isValidFileType(String contentType, String fileName) {
        if (contentType == null || fileName == null) return false;
        String name = fileName.toLowerCase();
        return (contentType.equals("application/pdf") && name.endsWith(".pdf")) ||
               (contentType.equals("image/jpeg") && (name.endsWith(".jpg") || name.endsWith(".jpeg"))) ||
               (contentType.equals("image/png") && name.endsWith(".png")) ||
               (contentType.equals("application/msword") && name.endsWith(".doc")) ||
               (contentType.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document") && name.endsWith(".docx"));
    }

    private String extraerDriveFileId(String ruta) {
        if (ruta != null && ruta.startsWith("drive:")) return ruta.substring(6);
        return null;
    }

    private boolean tieneContenido(byte[] contenido) {
        return contenido != null && contenido.length > 0;
    }

    private boolean tieneContenidoOStorage(byte[] contenido, String ruta) {
        return tieneContenido(contenido) || extraerDriveFileId(ruta) != null;
    }

    private String resolverAlmacenamiento(byte[] contenido, String ruta) {
        if (extraerDriveFileId(ruta) != null) return "DRIVE";
        if (tieneContenido(contenido)) return "BD";
        return "NINGUNO";
    }

    private String resolverClaveCertificado(String tipoCertificado) {
        String tipo = tipoCertificado == null ? "" : tipoCertificado.trim().toLowerCase();
        if (tipo.contains("electoral")) return "electoral";
        if (tipo.contains("jac") || tipo.contains("residencia")) return "residencia";
        return "sisben";
    }

    private boolean documentoCertificadoCargado(Tramite tramite, String claveCertificado) {
        if ("electoral".equals(claveCertificado)) return tieneContenidoOStorage(tramite.getContenidoCertificadoElectoral(), tramite.getRuta_certificado_electoral());
        if ("residencia".equals(claveCertificado)) return tieneContenidoOStorage(tramite.getContenidoDocumentoResidencia(), tramite.getRuta_certificado());
        return tieneContenidoOStorage(tramite.getContenidoCertificadoSisben(), tramite.getRuta_certificado_sisben());
    }

    private Long resolverUsuarioIdPorHeaders(String usernameHeader, String adminUsernameHeader) {
        String u = (usernameHeader != null && !usernameHeader.isBlank()) ? usernameHeader.trim() : 
                   (adminUsernameHeader != null && !adminUsernameHeader.isBlank() ? adminUsernameHeader.trim() : null);
        if (u == null) return null;
        return usuarioRepository.findByUsername(u).map(com.sistema.tramites.backend.usuario.Usuario::getId).orElse(null);
    }

    private String resolverAccionDocumento(String prefijo, String tipo, String accion) {
        String a = accion == null ? "" : accion.trim().toLowerCase();
        if (a.contains("ver") || a.contains("visualizar")) return prefijo + "_VISUALIZADO_" + tipo;
        if (a.contains("descargar")) return prefijo + "_DESCARGADO_" + tipo;
        return prefijo + "_ACCESO_" + tipo;
    }
}
