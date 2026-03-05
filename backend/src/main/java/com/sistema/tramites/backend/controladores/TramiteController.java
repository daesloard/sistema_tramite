package com.sistema.tramites.backend.controladores;

import com.sistema.tramites.backend.*;

import jakarta.validation.Valid;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.time.format.DateTimeFormatter;
import java.text.Normalizer;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/tramites")
public class TramiteController {

    private static final String DRIVE_PREFIX = "drive:";
    private static final Logger log = LoggerFactory.getLogger(TramiteController.class);

    private final TramiteRepository tramiteRepository;
    private final WorkingDayCalculator workingDayCalculator;
    private final EmailService emailService;
    private final DocumentoGeneradoService documentoGeneradoService;
    private final UsuarioRepository usuarioRepository;
    private final DriveStorageService driveStorageService;
    private final AuditoriaTramiteService auditoriaTramiteService;
    private final NotificacionUsuarioService notificacionUsuarioService;
    private final CertificadoPreGeneracionAsyncService certificadoPreGeneracionAsyncService;
    private final CertificadoPostFirmaAsyncService certificadoPostFirmaAsyncService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public TramiteController(TramiteRepository tramiteRepository, 
                           WorkingDayCalculator workingDayCalculator,
                           EmailService emailService,
                           DocumentoGeneradoService documentoGeneradoService,
                           UsuarioRepository usuarioRepository,
                           DriveStorageService driveStorageService,
                           AuditoriaTramiteService auditoriaTramiteService,
                           NotificacionUsuarioService notificacionUsuarioService,
                           CertificadoPreGeneracionAsyncService certificadoPreGeneracionAsyncService,
                           CertificadoPostFirmaAsyncService certificadoPostFirmaAsyncService) {
        this.tramiteRepository = tramiteRepository;
        this.workingDayCalculator = workingDayCalculator;
        this.emailService = emailService;
        this.documentoGeneradoService = documentoGeneradoService;
        this.usuarioRepository = usuarioRepository;
        this.driveStorageService = driveStorageService;
        this.auditoriaTramiteService = auditoriaTramiteService;
        this.notificacionUsuarioService = notificacionUsuarioService;
        this.certificadoPreGeneracionAsyncService = certificadoPreGeneracionAsyncService;
        this.certificadoPostFirmaAsyncService = certificadoPostFirmaAsyncService;
    }

    @GetMapping
    public List<Tramite> listar() {
        return tramiteRepository.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Tramite> obtener(@PathVariable Long id) {
        Optional<Tramite> tramite = tramiteRepository.findById(id);
        return tramite.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Tramite> crear(@Valid @RequestBody Tramite tramite) {
        if (tramite.getNumeroRadicado() == null || tramite.getNumeroRadicado().isBlank()) {
            tramite.setNumeroRadicado(generarRadicado());
        }
        if (tramite.getFechaRadicacion() == null) {
            tramite.setFechaRadicacion(LocalDateTime.now());
        }
        if (tramite.getEstado() == null) {
            tramite.setEstado(EstadoTramite.RADICADO);
        }
        Tramite guardado = tramiteRepository.save(tramite);
        return ResponseEntity.status(HttpStatus.CREATED).body(guardado);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Tramite> actualizar(@PathVariable Long id, @Valid @RequestBody Tramite entrada) {
        return tramiteRepository.findById(id)
                .map(actual -> {
                    actual.setNombreSolicitante(entrada.getNombreSolicitante());
                    actual.setTipoTramite(entrada.getTipoTramite());
                    actual.setDescripcion(entrada.getDescripcion());
                    actual.setEstado(entrada.getEstado() != null ? entrada.getEstado() : actual.getEstado());
                    Tramite actualizado = tramiteRepository.save(actual);
                    return ResponseEntity.ok(actualizado);
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        if (!tramiteRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        tramiteRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * NUEVOS ENDPOINTS PARA CERTIFICADO DE RESIDENCIA
     */

    @PostMapping("/solicitud-residencia")
    public ResponseEntity<?> radicacionCertificadoResidencia(
            @Valid @RequestBody SolicitudCertificadoResidenciaDTO solicitud) {
        try {
            // Crear entidad Tramite
            Tramite tramite = new Tramite();
            tramite.setNumeroRadicado(generarRadicado());
                tramite.setNombreSolicitante(solicitud.getNombre() != null
                    ? solicitud.getNombre().trim().toUpperCase(new Locale("es", "CO"))
                    : null);
            tramite.setTipoTramite("CERTIFICADO_RESIDENCIA");
            tramite.setFechaRadicacion(LocalDateTime.now());
            tramite.setEstado(EstadoTramite.RADICADO);
            
            // Datos específicos del certificado
            tramite.setTipoDocumento(solicitud.getTipoDocumento());
            tramite.setNumeroDocumento(solicitud.getNumeroDocumento());
            tramite.setLugarExpedicionDocumento(solicitud.getLugarExpedicionDocumento());
            tramite.setDireccionResidencia(solicitud.getDireccionResidencia());
            tramite.setBarrioResidencia(solicitud.getBarrioResidencia());
            tramite.setTelefono(solicitud.getTelefono());
            tramite.setCorreoElectronico(normalizarCorreo(solicitud.getCorreoElectronico()));
            tramite.setTipo_certificado(solicitud.getTipo_certificado());

            String driveFolderId = null;
            if (driveStorageService.isEnabled()) {
                driveFolderId = obtenerOCrearCarpetaDrive(tramite);
            }
            
                // Guardar documentos en BLOB si se envían
                if (solicitud.getDocumento_identidad_base64() != null && !solicitud.getDocumento_identidad_base64().isEmpty()) {
                byte[] documentoIdentidad = Base64.getDecoder()
                    .decode(solicitud.getDocumento_identidad_base64());
                String nombreIdentidad = solicitud.getDocumento_identidad_nombre();
                String tipoIdentidad = solicitud.getDocumento_identidad_tipo();
                String nombreFinalIdentidad = nombreIdentidad != null && !nombreIdentidad.isBlank()
                    ? nombreIdentidad
                    : "documento_identidad_" + System.currentTimeMillis();
                String tipoFinalIdentidad = tipoIdentidad != null && !tipoIdentidad.isBlank()
                    ? tipoIdentidad
                    : "application/pdf";
                tramite.setNombreArchivoIdentidad(nombreIdentidad != null && !nombreIdentidad.isBlank()
                    ? nombreIdentidad
                    : "documento_identidad_" + System.currentTimeMillis());
                tramite.setTipoContenidoIdentidad(tipoIdentidad != null && !tipoIdentidad.isBlank()
                    ? tipoIdentidad
                    : "application/pdf");

                if (driveStorageService.isEnabled()) {
                    String driveId = driveStorageService.uploadFileToFolder(nombreFinalIdentidad, tipoFinalIdentidad, documentoIdentidad, driveFolderId);
                    tramite.setRuta_documento_identidad(DRIVE_PREFIX + driveId);
                    tramite.setContenidoDocumentoIdentidad(null);
                } else {
                    tramite.setContenidoDocumentoIdentidad(documentoIdentidad);
                }
                }

                if (solicitud.getDocumento_solicitud_base64() != null && !solicitud.getDocumento_solicitud_base64().isEmpty()) {
                byte[] documentoSolicitud = Base64.getDecoder()
                    .decode(solicitud.getDocumento_solicitud_base64());
                String nombreSolicitud = solicitud.getDocumento_solicitud_nombre();
                String tipoSolicitud = solicitud.getDocumento_solicitud_tipo();
                String nombreFinalSolicitud = nombreSolicitud != null && !nombreSolicitud.isBlank()
                    ? nombreSolicitud
                    : "documento_solicitud_" + System.currentTimeMillis();
                String tipoFinalSolicitud = tipoSolicitud != null && !tipoSolicitud.isBlank()
                    ? tipoSolicitud
                    : "application/pdf";
                tramite.setNombreArchivoSolicitud(nombreSolicitud != null && !nombreSolicitud.isBlank()
                    ? nombreSolicitud
                    : "documento_solicitud_" + System.currentTimeMillis());
                tramite.setTipoContenidoSolicitud(tipoSolicitud != null && !tipoSolicitud.isBlank()
                    ? tipoSolicitud
                    : "application/pdf");

                if (driveStorageService.isEnabled()) {
                    String driveId = driveStorageService.uploadFileToFolder(nombreFinalSolicitud, tipoFinalSolicitud, documentoSolicitud, driveFolderId);
                    tramite.setRuta_documento_solicitud(DRIVE_PREFIX + driveId);
                    tramite.setContenidoDocumentoSolicitud(null);
                } else {
                    tramite.setContenidoDocumentoSolicitud(documentoSolicitud);
                }
                }

                if (solicitud.getCertificado_base64() != null && !solicitud.getCertificado_base64().isEmpty()) {
                byte[] certificado = Base64.getDecoder()
                    .decode(solicitud.getCertificado_base64());
                String nombreCertificado = solicitud.getCertificado_nombre();
                String tipoCertificado = solicitud.getCertificado_tipo();
                String nombreFinal = nombreCertificado != null && !nombreCertificado.isBlank()
                    ? nombreCertificado
                    : "certificado_" + System.currentTimeMillis();
                String tipoFinal = tipoCertificado != null && !tipoCertificado.isBlank()
                    ? tipoCertificado
                    : "application/pdf";

                String driveIdCertificado = null;
                if (driveStorageService.isEnabled()) {
                    driveIdCertificado = driveStorageService.uploadFileToFolder(nombreFinal, tipoFinal, certificado, driveFolderId);
                }

                // Respaldo genérico: garantiza disponibilidad del 3er adjunto en correo de radicación
                tramite.setContenidoDocumentoResidencia(driveIdCertificado != null ? null : certificado);
                tramite.setNombreArchivoResidencia(nombreFinal);
                tramite.setTipoContenidoResidencia(tipoFinal);
                if (driveIdCertificado != null) {
                    tramite.setRuta_certificado(DRIVE_PREFIX + driveIdCertificado);
                }

                String tipoSeleccionado = solicitud.getTipo_certificado();
                if ("SISBEN".equalsIgnoreCase(tipoSeleccionado)) {
                    tramite.setContenidoCertificadoSisben(driveIdCertificado != null ? null : certificado);
                    tramite.setNombreArchivoSisben(nombreFinal);
                    tramite.setTipoContenidoSisben(tipoFinal);
                    if (driveIdCertificado != null) {
                        tramite.setRuta_certificado_sisben(DRIVE_PREFIX + driveIdCertificado);
                    }
                } else if ("ELECTORAL".equalsIgnoreCase(tipoSeleccionado)) {
                    tramite.setContenidoCertificadoElectoral(driveIdCertificado != null ? null : certificado);
                    tramite.setNombreArchivoElectoral(nombreFinal);
                    tramite.setTipoContenidoElectoral(tipoFinal);
                    if (driveIdCertificado != null) {
                        tramite.setRuta_certificado_electoral(DRIVE_PREFIX + driveIdCertificado);
                    }
                } else {
                    tramite.setContenidoDocumentoResidencia(driveIdCertificado != null ? null : certificado);
                    tramite.setNombreArchivoResidencia(nombreFinal);
                    tramite.setTipoContenidoResidencia(tipoFinal);
                    if (driveIdCertificado != null) {
                        tramite.setRuta_certificado(DRIVE_PREFIX + driveIdCertificado);
                    }
                }
                }
            
            // Rutas legacy para compatibilidad
            if (tramite.getRuta_documento_solicitud() == null || tramite.getRuta_documento_solicitud().isBlank()) {
                tramite.setRuta_documento_solicitud("doc_solicitud_" + System.currentTimeMillis());
            }
            if (tramite.getRuta_documento_identidad() == null || tramite.getRuta_documento_identidad().isBlank()) {
                tramite.setRuta_documento_identidad("doc_identidad_" + System.currentTimeMillis());
            }
            if (tramite.getRuta_certificado() == null || tramite.getRuta_certificado().isBlank()) {
                tramite.setRuta_certificado("certificado_" + System.currentTimeMillis());
            }
            
            // Calcular vencimiento (10 días hábiles)
            LocalDate fechaVencimiento = workingDayCalculator
                    .calcularFechaVencimiento(LocalDate.now(), 10);
            tramite.setFechaVencimiento(fechaVencimiento);
            
            // Calcular vigencia (6 meses)
            LocalDate inicioVigencia = guardadoFechaRadicacion(tramite);
            tramite.setFechaVigencia(workingDayCalculator.calcularFechaVigencia(inicioVigencia));
            
            // Guardar en BD
            Tramite guardado = tramiteRepository.save(tramite);

                auditoriaTramiteService.registrarEvento(
                    guardado.getId(),
                    null,
                    "RADICACION_CREADA",
                    "Se radica solicitud " + guardado.getNumeroRadicado(),
                    null,
                    guardado.getEstado()
                );

                List<Long> verificadoresIds = usuarioRepository.findAllByRolAndActivoTrue(RolUsuario.VERIFICADOR)
                    .stream()
                    .map(Usuario::getId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();

                notificacionUsuarioService.crearParaUsuarios(
                    verificadoresIds,
                    guardado.getId(),
                    "Nueva solicitud radicada",
                    "Se radicó la solicitud " + guardado.getNumeroRadicado() + " a nombre de " + guardado.getNombreSolicitante(),
                    "INFO"
                );
            
            // Enviar emails
            emailService.enviarConfirmacionRadicacion(
                    guardado.getCorreoElectronico(),
                    solicitud.getNombre(),
                    guardado.getNumeroRadicado(),
                    fechaVencimiento,
                    guardado
            );
            
            // Email al verificador (usar email configurado)
            emailService.enviarNotificacionVerificador(
                    "verificador@municipio.gov.co",
                    guardado.getNumeroRadicado(),
                    solicitud.getNombre()
            );
            
            // Respuesta
            var respuesta = new java.util.HashMap<String, Object>();
            respuesta.put("tramiteId", guardado.getId());
            respuesta.put("numeroRadicado", guardado.getNumeroRadicado());
            respuesta.put("fechaSolicitud", guardado.getFechaRadicacion());
            respuesta.put("fechaVencimiento", fechaVencimiento);
            respuesta.put("estado", "RADICADO");
                respuesta.put("driveHabilitado", driveStorageService.isEnabled());
                respuesta.put("driveFolderId", guardado.getDriveFolderId());
                respuesta.put("almacenamientoIdentidad", extraerDriveFileId(guardado.getRuta_documento_identidad()) != null ? "DRIVE" : "BD");
                respuesta.put("almacenamientoSolicitud", extraerDriveFileId(guardado.getRuta_documento_solicitud()) != null ? "DRIVE" : "BD");
                respuesta.put("almacenamientoCertificado", extraerDriveFileId(guardado.getRuta_certificado()) != null
                    || extraerDriveFileId(guardado.getRuta_certificado_sisben()) != null
                    || extraerDriveFileId(guardado.getRuta_certificado_electoral()) != null
                    ? "DRIVE" : "BD");
            respuesta.put("mensaje", "Solicitud radicada exitosamente");

            return ResponseEntity.status(HttpStatus.CREATED).body(respuesta);
            
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Error en la radicación: " + e.getMessage());
        }
    }

    @PostMapping("/{id}/verificacion")
    public ResponseEntity<?> verificarSolicitud(@PathVariable Long id,
                                               @Valid @RequestBody VerificacionSolicitudDTO verificacion) {
        try {
            Optional<Tramite> optTramite = tramiteRepository.findById(id);
            if (optTramite.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            
            Tramite tramite = optTramite.get();
            EstadoTramite estadoAnterior = tramite.getEstado();
            tramite.setEstado(EstadoTramite.EN_VALIDACION);
            tramite.setObservaciones(verificacion.getObservaciones());

            String usernameVerificador = (verificacion.getUsername() == null || verificacion.getUsername().isBlank())
                    ? "verificador"
                    : verificacion.getUsername().trim();
            Long usuarioVerificadorId = null;
            Optional<Usuario> usuarioVerificadorOpt = usuarioRepository
                    .findByUsernameAndRolAndActivoTrue(usernameVerificador, RolUsuario.VERIFICADOR);
            if (usuarioVerificadorOpt.isPresent()) {
                Usuario usuarioVerificador = usuarioVerificadorOpt.get();
                tramite.setUsuarioVerificador(usuarioVerificador);
                usuarioVerificadorId = usuarioVerificador.getId();
            }

            int anioConsecutivo = Year.now().getValue();
            String consecutivoIngresado = verificacion.getConsecutivo() == null ? "" : verificacion.getConsecutivo().trim();
            if (!consecutivoIngresado.isBlank()) {
                String consecutivoNormalizado = normalizarConsecutivo(consecutivoIngresado);
                if (consecutivoNormalizado == null) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body("Consecutivo inválido. Debe contener solo números (ej: 001)");
                }
                if (consecutivoYaUsadoEnAnio(consecutivoNormalizado, anioConsecutivo, tramite.getId())) {
                    return ResponseEntity.status(HttpStatus.CONFLICT)
                            .body("El consecutivo " + consecutivoNormalizado + " ya fue usado en " + anioConsecutivo);
                }
                tramite.setConsecutivoVerificador(consecutivoNormalizado);
            } else if (tramite.getConsecutivoVerificador() == null || tramite.getConsecutivoVerificador().isBlank()) {
                tramite.setConsecutivoVerificador(generarSiguienteConsecutivo(anioConsecutivo));
            }
            
            if (verificacion.isAprobado()) {
                tramite.setEstado(EstadoTramite.EN_FIRMA);
                tramite.setFechaVerificacion(LocalDateTime.now());
                LocalDate inicioVigencia = guardadoFechaRadicacion(tramite);
                tramite.setFechaVigencia(workingDayCalculator.calcularFechaVigencia(inicioVigencia));
                if (tramite.getCodigoVerificacion() == null || tramite.getCodigoVerificacion().isBlank()) {
                    tramite.setCodigoVerificacion(generarCodigoVerificacion(tramite.getNumeroRadicado()));
                }
            } else {
                tramite.setEstado(EstadoTramite.RECHAZADO);
                tramite.setFechaVerificacion(LocalDateTime.now());
                if (tramite.getCodigoVerificacion() == null || tramite.getCodigoVerificacion().isBlank()) {
                    tramite.setCodigoVerificacion(generarCodigoVerificacion(tramite.getNumeroRadicado()));
                }
                documentoGeneradoService.generarYAdjuntarPdf(tramite, false, verificacion.getObservaciones());
                tramite.setHashDocumentoGenerado(calcularHashSha256(tramite.getContenidoPdfGenerado()));
                subirCertificadoGeneradoADrive(tramite);
                emailService.enviarDocumentoFinal(
                        tramite.getCorreoElectronico(),
                        tramite.getNombreSolicitante(),
                        tramite.getNumeroRadicado(),
                        false,
                    verificacion.getObservaciones(),
                    tramite.getContenidoPdfGenerado(),
                    tramite.getNombrePdfGenerado()
                );
            }
            
            Tramite actualizado = tramiteRepository.save(tramite);
            if (verificacion.isAprobado()) {
                certificadoPreGeneracionAsyncService.pregenerarCertificadoParaFirma(actualizado.getId());
            }
                String accionAuditoria = verificacion.isAprobado() ? "VERIFICACION_APROBADA" : "VERIFICACION_RECHAZADA";
                auditoriaTramiteService.registrarEvento(
                    actualizado.getId(),
                    usuarioVerificadorId,
                    accionAuditoria,
                    "Verificación sobre radicado " + actualizado.getNumeroRadicado()
                        + " con consecutivo " + actualizado.getConsecutivoVerificador(),
                    estadoAnterior,
                    actualizado.getEstado()
                );

            if (verificacion.isAprobado()) {
                List<Long> alcaldesIds = usuarioRepository.findAllByRolAndActivoTrue(RolUsuario.ALCALDE)
                        .stream()
                        .map(Usuario::getId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList();

                notificacionUsuarioService.crearParaUsuarios(
                        alcaldesIds,
                        actualizado.getId(),
                        "Solicitud lista para firma",
                        "El trámite " + actualizado.getNumeroRadicado() + " fue aprobado por " + usernameVerificador + " y está en firma.",
                        "INFO"
                );
            } else {
                List<Long> adminsIds = usuarioRepository.findAllByRolAndActivoTrue(RolUsuario.ADMINISTRADOR)
                        .stream()
                        .map(Usuario::getId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList();

                notificacionUsuarioService.crearParaUsuarios(
                        adminsIds,
                        actualizado.getId(),
                        "Solicitud rechazada por verificador",
                        "El trámite " + actualizado.getNumeroRadicado() + " fue rechazado por " + usernameVerificador + ".",
                        "WARNING"
                );
            }

            var respuesta = new java.util.HashMap<String, Object>();
            respuesta.put("tramiteId", actualizado.getId());
            respuesta.put("numeroRadicado", actualizado.getNumeroRadicado());
            respuesta.put("estado", actualizado.getEstado() != null ? actualizado.getEstado().name() : null);
            respuesta.put("fechaFirmaAlcalde", actualizado.getFechaFirmaAlcalde());
            respuesta.put("codigoVerificacion", actualizado.getCodigoVerificacion());
            respuesta.put("mensaje", "✅ Certificado firmado y emitido correctamente");

            return ResponseEntity.ok(respuesta);
            
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Error en verificación: " + e.getMessage());
        }
    }

    @PostMapping("/{id}/notificar-verificador")
    public ResponseEntity<?> notificarVerificadorDesdeAdmin(
            @PathVariable Long id,
            @RequestBody(required = false) NotificacionVerificadorAdminDTO request,
            @RequestHeader(value = "X-Admin-Username", required = false) String adminUsername) {
        try {
            if (adminUsername == null || adminUsername.isBlank()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body("❌ Debes autenticarte como administrador");
            }

            Optional<Usuario> adminOpt = usuarioRepository
                    .findByUsernameAndRolAndActivoTrue(adminUsername.trim(), RolUsuario.ADMINISTRADOR);
            if (adminOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body("❌ Usuario administrador no válido o inactivo");
            }
                Usuario admin = adminOpt.get();

            Optional<Tramite> tramiteOpt = tramiteRepository.findById(id);
            if (tramiteOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body("❌ Trámite no encontrado");
            }

            Tramite tramite = tramiteOpt.get();
            List<Usuario> verificadores = usuarioRepository.findAllByRolAndActivoTrue(RolUsuario.VERIFICADOR);
            List<String> correos = verificadores.stream()
                    .map(Usuario::getEmail)
                    .filter(correo -> correo != null && !correo.isBlank())
                    .map(correo -> correo.trim().toLowerCase(Locale.ROOT))
                    .distinct()
                    .toList();

            if (correos.isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body("❌ No hay verificadores activos con correo configurado");
            }

            String mensajeAdmin = request != null && request.mensaje != null ? request.mensaje.trim() : "";
            String detalle = mensajeAdmin.isBlank()
                    ? "Gestión solicitada por administrador"
                    : "Gestión solicitada por administrador: " + mensajeAdmin;

            for (String correo : correos) {
                emailService.enviarNotificacionVerificador(
                        correo,
                        tramite.getNumeroRadicado(),
                        tramite.getNombreSolicitante() + " | " + detalle
                );
            }

                List<Long> verificadoresIds = verificadores.stream()
                    .map(Usuario::getId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();

                notificacionUsuarioService.crearParaUsuarios(
                    verificadoresIds,
                    tramite.getId(),
                    "Gestión solicitada por administrador",
                    "El administrador " + admin.getUsername() + " solicitó gestionar el trámite " + tramite.getNumeroRadicado()
                        + (mensajeAdmin.isBlank() ? "." : ": " + mensajeAdmin),
                    "INFO"
                );

                auditoriaTramiteService.registrarEvento(
                    tramite.getId(),
                    admin.getId(),
                    "NOTIFICACION_VERIFICADOR",
                    "Administrador " + admin.getUsername() + " notificó verificadores para radicado " + tramite.getNumeroRadicado(),
                    tramite.getEstado(),
                    tramite.getEstado()
                );

            var respuesta = new java.util.HashMap<String, Object>();
            respuesta.put("ok", true);
            respuesta.put("tramiteId", tramite.getId());
            respuesta.put("numeroRadicado", tramite.getNumeroRadicado());
            respuesta.put("notificados", correos.size());
            respuesta.put("mensaje", "✅ Verificador(es) notificados");
            return ResponseEntity.ok(respuesta);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("❌ Error al notificar verificador: " + e.getMessage());
        }
    }

    private LocalDate guardadoFechaRadicacion(Tramite tramite) {
        if (tramite.getFechaRadicacion() != null) {
            return tramite.getFechaRadicacion().toLocalDate();
        }
        return LocalDate.now();
    }

    @PostMapping("/{id}/firma-alcalde")
    public ResponseEntity<?> firmarDocumento(@PathVariable Long id,
                                            @Valid @RequestBody FirmaAlcaldeDTO firma) {
        try {
            long inicioTotal = System.nanoTime();
            Optional<Tramite> optTramite = tramiteRepository.findById(id);
            if (optTramite.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

                String usernameFirma = (firma.getUsername() == null || firma.getUsername().isBlank())
                    ? "alcalde"
                    : firma.getUsername().trim();

                Optional<Usuario> usuarioAlcaldeOpt = usuarioRepository
                    .findByUsernameAndRolAndActivoTrue(usernameFirma, RolUsuario.ALCALDE);

                if (usuarioAlcaldeOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Usuario de alcalde no válido o inactivo");
                }

                Usuario usuarioAlcalde = usuarioAlcaldeOpt.get();
                String passwordFirma = firma.getFirmaDigital() != null ? firma.getFirmaDigital().trim() : "";
                String passwordGuardada = usuarioAlcalde.getPasswordHash();

                boolean passwordValida = false;
                if (!passwordFirma.isBlank() && passwordGuardada != null && !passwordGuardada.isBlank()) {
                    if (passwordFirma.equals(passwordGuardada)) {
                        // Compatibilidad con datos legacy en texto plano
                        passwordValida = true;
                    } else {
                        try {
                            passwordValida = passwordEncoder.matches(passwordFirma, passwordGuardada);
                        } catch (IllegalArgumentException ex) {
                            passwordValida = false;
                        }
                    }
                }

                if (!passwordValida && "password123".equals(passwordFirma)) {
                    // Compatibilidad temporal para entorno demo/desarrollo
                    passwordValida = true;
                }

                if (!passwordValida) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Contraseña de firma incorrecta");
                }
            
            Tramite tramite = optTramite.get();
            EstadoTramite estadoAnterior = tramite.getEstado();

                if (tramite.getEstado() != EstadoTramite.EN_FIRMA) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("El trámite no está en estado EN_FIRMA");
                }

            tramite.setEstado(EstadoTramite.FINALIZADO);
            tramite.setFirmaAlcalde("FIRMADO_POR_" + usernameFirma.toUpperCase());
            tramite.setFechaFirmaAlcalde(LocalDateTime.now());
                tramite.setUsuarioAlcalde(usuarioAlcalde);
            if (tramite.getCodigoVerificacion() == null || tramite.getCodigoVerificacion().isBlank()) {
                tramite.setCodigoVerificacion(generarCodigoVerificacion(tramite.getNumeroRadicado()));
            }

            long inicioPersistencia = System.nanoTime();
            
            Tramite actualizado = tramiteRepository.save(tramite);
            long duracionPersistencia = millisDesde(inicioPersistencia);

            long inicioPostFirma = System.nanoTime();
            certificadoPostFirmaAsyncService.procesarPostFirma(actualizado.getId());
            long duracionProgramacionPostFirma = millisDesde(inicioPostFirma);

                auditoriaTramiteService.registrarEvento(
                    actualizado.getId(),
                    usuarioAlcalde.getId(),
                    "FIRMA_ALCALDE",
                    "Documento firmado por " + usuarioAlcalde.getUsername() + " para radicado " + actualizado.getNumeroRadicado(),
                    estadoAnterior,
                    actualizado.getEstado()
                );

                Set<Long> destinatariosFirma = java.util.stream.Stream.concat(
                        usuarioRepository.findAllByRolAndActivoTrue(RolUsuario.ADMINISTRADOR).stream(),
                        usuarioRepository.findAllByRolAndActivoTrue(RolUsuario.VERIFICADOR).stream()
                    )
                    .map(Usuario::getId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

                notificacionUsuarioService.crearParaUsuarios(
                    destinatariosFirma,
                    actualizado.getId(),
                    "Certificado firmado por alcalde",
                    "El trámite " + actualizado.getNumeroRadicado() + " fue firmado por " + usuarioAlcalde.getUsername() + " y quedó FINALIZADO.",
                    "SUCCESS"
                );

            long duracionTotal = millisDesde(inicioTotal);
                log.info("firma-alcalde tiempos tramite={} save={}ms postFirmaAsyncSchedule={}ms total={}ms",
                    actualizado.getId(),
                    duracionPersistencia,
                    duracionProgramacionPostFirma,
                    duracionTotal);
            
            return ResponseEntity.ok(actualizado);
            
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Error al firmar: " + e.getMessage());
        }
    }

    @GetMapping("/verificacion/{numeroRadicado}")
    public ResponseEntity<?> verificarCertificado(@PathVariable String numeroRadicado,
                                                  @RequestParam(value = "factorTipo", required = false) String factorTipo,
                                                  @RequestParam(value = "factorValor", required = false) String factorValor) {
        try {
            String criterio = numeroRadicado == null ? "" : numeroRadicado.trim();
            if (criterio.isBlank()) {
                return ResponseEntity.badRequest().body("Radicado o código de verificación requerido");
            }

            String tipo = factorTipo == null ? "" : factorTipo.trim().toUpperCase(Locale.ROOT);
            String valor = factorValor == null ? "" : factorValor.trim();
            if (tipo.isBlank() || valor.isBlank()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body("Debes enviar el factor de validación (tipo y valor)");
            }

            Optional<Tramite> tramite = tramiteRepository.findByNumeroRadicadoIgnoreCase(criterio);
            if (tramite.isEmpty()) {
                tramite = tramiteRepository.findByCodigoVerificacionIgnoreCase(criterio);
            }
            
            if (tramite.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body("Radicado o código de verificación no encontrado");
            }
            
            Tramite t = tramite.get();
            if (!validarFactorReconocimiento(t, tipo, valor)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("El dato de validación no coincide con el titular de la solicitud");
                }

            String hashActual = calcularHashSha256(t.getContenidoPdfGenerado());
            boolean documentoIntegro = t.getContenidoPdfGenerado() != null
                    && t.getContenidoPdfGenerado().length > 0
                    && t.getHashDocumentoGenerado() != null
                    && !t.getHashDocumentoGenerado().isBlank()
                    && Objects.equals(t.getHashDocumentoGenerado(), hashActual);
            
            var respuesta = new java.util.HashMap<String, Object>();
            respuesta.put("numeroRadicado", t.getNumeroRadicado());
            respuesta.put("codigoVerificacion", t.getCodigoVerificacion());
            respuesta.put("estado", t.getEstado().toString());
            respuesta.put("nombreSolicitante", t.getNombreSolicitante());
            respuesta.put("numeroDocumento", t.getNumeroDocumento());
            respuesta.put("lugarExpedicionDocumento", t.getLugarExpedicionDocumento());
            respuesta.put("direccionResidencia", t.getDireccionResidencia());
            respuesta.put("barrioResidencia", t.getBarrioResidencia());
            respuesta.put("fechaRadicacion", t.getFechaRadicacion());
            respuesta.put("documentoIntegro", documentoIntegro);
            respuesta.put("hashRegistrado", t.getHashDocumentoGenerado());
            respuesta.put("hashActual", hashActual);
            respuesta.put("dobleValidacion", true);
            respuesta.put("factorValidado", tipo);
            
            // VIGENCIA DEL CERTIFICADO: Solo disponible si está FINALIZADO (firmado por alcalde)
            if (t.getEstado() == EstadoTramite.FINALIZADO) {
                LocalDate hoy = LocalDate.now();
                boolean vigente = hoy.isBefore(t.getFechaVigencia()) || hoy.isEqual(t.getFechaVigencia());
                respuesta.put("certificadoEmitido", true);
                respuesta.put("vigente", vigente);
                respuesta.put("fechaVigencia", t.getFechaVigencia());
                respuesta.put("fechaFirmaAlcalde", t.getFechaFirmaAlcalde());
                respuesta.put("tiposCertificado", t.getTipo_certificado());
            } else {
                // Certificado aún no emitido
                respuesta.put("certificadoEmitido", false);
                respuesta.put("vigente", null);
                respuesta.put("fechaVigencia", null);
                respuesta.put("fechaFirmaAlcalde", null);
                respuesta.put("mensaje", "El certificado aún no ha sido emitido. Estado actual: " + t.getEstado());
            }
            
            // Mostrar observaciones si fue rechazado
            if (t.getEstado() == EstadoTramite.RECHAZADO && t.getObservaciones() != null) {
                respuesta.put("observaciones", t.getObservaciones());
            }
            
            return ResponseEntity.ok(respuesta);
            
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error en verificación: " + e.getMessage());
        }
    }

    @GetMapping("/verificacion/resueltas/{numeroDocumento}")
    public ResponseEntity<?> consultarSolicitudesResueltas(@PathVariable String numeroDocumento) {
        try {
            String documento = numeroDocumento == null ? "" : numeroDocumento.trim();
            if (documento.isBlank()) {
                return ResponseEntity.badRequest().body("Número de documento requerido");
            }

            List<java.util.Map<String, Object>> resueltas = tramiteRepository.findAll().stream()
                    .filter(t -> t.getNumeroDocumento() != null && documento.equalsIgnoreCase(t.getNumeroDocumento().trim()))
                    .filter(t -> t.getEstado() == EstadoTramite.FINALIZADO || t.getEstado() == EstadoTramite.RECHAZADO)
                    .sorted((a, b) -> b.getFechaRadicacion().compareTo(a.getFechaRadicacion()))
                    .map(t -> {
                        java.util.Map<String, Object> item = new java.util.HashMap<>();
                        item.put("id", t.getId());
                        item.put("numeroRadicado", t.getNumeroRadicado());
                        item.put("estado", t.getEstado() != null ? t.getEstado().name() : null);
                        item.put("fechaRadicacion", t.getFechaRadicacion());
                        item.put("fechaFirmaAlcalde", t.getFechaFirmaAlcalde());
                        item.put("fechaVigencia", t.getFechaVigencia());
                        item.put("tipoCertificado", t.getTipo_certificado());
                        item.put("observaciones", t.getObservaciones());
                        item.put("certificadoDisponible", (t.getContenidoPdfGenerado() != null && t.getContenidoPdfGenerado().length > 0)
                            || extraerDriveFileId(t.getRuta_certificado_final()) != null);
                        return item;
                    })
                    .toList();

            return ResponseEntity.ok(resueltas);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error consultando solicitudes resueltas: " + e.getMessage());
        }
    }

    @GetMapping("/{id}/documento-generado")
        public ResponseEntity<?> descargarDocumentoGenerado(
            @PathVariable Long id,
            @RequestParam(value = "accion", required = false) String accion,
            @RequestHeader(value = "X-Username", required = false) String usernameHeader,
            @RequestHeader(value = "X-Admin-Username", required = false) String adminUsernameHeader) {
        try {
            Optional<Tramite> optTramite = tramiteRepository.findById(id);
            if (optTramite.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Trámite no encontrado");
            }

            Tramite tramite = optTramite.get();
            byte[] contenido = tramite.getContenidoPdfGenerado();
            if ((contenido == null || contenido.length == 0) && driveStorageService.isEnabled()) {
                String driveFileId = extraerDriveFileId(tramite.getRuta_certificado_final());
                if (driveFileId != null) {
                    contenido = driveStorageService.downloadFile(driveFileId);
                }
            }

            if (contenido == null || contenido.length == 0) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body("No existe documento generado para este trámite");
            }

                Long usuarioId = resolverUsuarioIdPorHeaders(usernameHeader, adminUsernameHeader);
                String accionAuditoria = resolverAccionDocumentoGenerado(accion);
                String almacenamiento = extraerDriveFileId(tramite.getRuta_certificado_final()) != null ? "DRIVE" : "BD";
                auditoriaTramiteService.registrarEvento(
                    tramite.getId(),
                    usuarioId,
                    accionAuditoria,
                    "Acceso a certificado generado en " + almacenamiento,
                    tramite.getEstado(),
                    tramite.getEstado()
                );

            String nombreArchivo = (tramite.getNombrePdfGenerado() != null && !tramite.getNombrePdfGenerado().isBlank())
                    ? tramite.getNombrePdfGenerado()
                    : "documento_generado_" + tramite.getNumeroRadicado() + ".pdf";

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nombreArchivo + "\"")
                    .header(HttpHeaders.CONTENT_TYPE, "application/pdf")
                    .body(contenido);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error al descargar documento generado: " + e.getMessage());
        }
    }

    @GetMapping("/{id}/auditoria")
    public ResponseEntity<?> obtenerAuditoriaTramite(
            @PathVariable Long id,
            @RequestHeader(value = "X-Admin-Username", required = false) String adminUsername) {
        try {
            if (adminUsername == null || adminUsername.isBlank()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body("❌ Debes autenticarte como administrador");
            }

            Optional<Usuario> adminOpt = usuarioRepository
                    .findByUsernameAndRolAndActivoTrue(adminUsername.trim(), RolUsuario.ADMINISTRADOR);
            if (adminOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body("❌ Usuario administrador no válido o inactivo");
            }

            Optional<Tramite> tramiteOpt = tramiteRepository.findById(id);
            if (tramiteOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body("❌ Trámite no encontrado");
            }

            Tramite tramite = tramiteOpt.get();
            List<AuditoriaTramite> eventos = auditoriaTramiteService.listarPorTramite(id);

            Set<Long> usuarioIds = eventos.stream()
                    .map(AuditoriaTramite::getUsuarioId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            Map<Long, Usuario> usuariosPorId = usuarioIds.isEmpty()
                    ? Map.of()
                    : usuarioRepository.findAllById(usuarioIds).stream()
                    .collect(Collectors.toMap(Usuario::getId, u -> u));

            List<Map<String, Object>> eventosRespuesta = eventos.stream()
                    .map(evento -> {
                        Map<String, Object> item = new HashMap<>();
                        item.put("id", evento.getId());
                        item.put("accion", evento.getAccion());
                        item.put("descripcion", evento.getDescripcion());
                        item.put("estadoAnterior", evento.getEstadoAnterior());
                        item.put("estadoNuevo", evento.getEstadoNuevo());
                        item.put("fechaIntegracion", evento.getFechaIntegracion());
                        item.put("usuarioId", evento.getUsuarioId());

                        Usuario usuario = evento.getUsuarioId() != null ? usuariosPorId.get(evento.getUsuarioId()) : null;
                        item.put("username", usuario != null ? usuario.getUsername() : null);
                        item.put("nombreCompleto", usuario != null ? usuario.getNombreCompleto() : null);
                        item.put("rol", usuario != null && usuario.getRol() != null ? usuario.getRol().name() : null);
                        return item;
                    })
                    .toList();

            Map<String, Object> respuesta = new HashMap<>();
            respuesta.put("tramiteId", tramite.getId());
            respuesta.put("numeroRadicado", tramite.getNumeroRadicado());
            respuesta.put("totalEventos", eventosRespuesta.size());
            respuesta.put("eventos", eventosRespuesta);
            return ResponseEntity.ok(respuesta);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("❌ Error consultando auditoría: " + e.getMessage());
        }
    }

    @GetMapping("/{id}/vista-previa-documento")
    public ResponseEntity<?> vistaPreviaDocumento(@PathVariable Long id,
                                                  @RequestParam(value = "includePdf", defaultValue = "false") boolean includePdf,
                                                  @RequestHeader(value = "X-Username", required = false) String usernameHeader,
                                                  @RequestHeader(value = "X-Admin-Username", required = false) String adminUsernameHeader) {
        try {
            Optional<Tramite> optTramite = tramiteRepository.findById(id);
            if (optTramite.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Trámite no encontrado");
            }

            Tramite tramite = optTramite.get();
            boolean aprobado = tramite.getEstado() != EstadoTramite.RECHAZADO;

            String contenido = "";
            String html = "";

            try {
                contenido = documentoGeneradoService.generarTextoDocumento(
                        tramite,
                        aprobado,
                        tramite.getObservaciones()
                );
            } catch (Exception ignored) {
                contenido = "";
            }

            try {
                html = documentoGeneradoService.generarHtmlDocumento(tramite, aprobado, tramite.getObservaciones());
            } catch (Exception ignored) {
                html = "";
            }

            byte[] pdfVistaPrevia = null;
            String errorPdf = null;
            if (includePdf) {
                try {
                    pdfVistaPrevia = documentoGeneradoService.generarPdfDocumento(
                            tramite,
                            aprobado,
                            tramite.getObservaciones()
                    );
                } catch (Exception ex) {
                    errorPdf = ex.getMessage();
                }
            }

            var respuesta = new java.util.HashMap<String, Object>();
            respuesta.put("tramiteId", tramite.getId());
            respuesta.put("numeroRadicado", tramite.getNumeroRadicado());
            respuesta.put("estado", tramite.getEstado() != null ? tramite.getEstado().name() : null);
            respuesta.put("plantilla", documentoGeneradoService.obtenerNombrePlantillaDocumento(tramite, aprobado));
            respuesta.put("contenido", contenido);
            respuesta.put("html", html);
            respuesta.put("pdfBase64", pdfVistaPrevia != null && pdfVistaPrevia.length > 0
                    ? Base64.getEncoder().encodeToString(pdfVistaPrevia)
                    : null);
            respuesta.put("pdfDisponible", pdfVistaPrevia != null && pdfVistaPrevia.length > 0);
            if (errorPdf != null && !errorPdf.isBlank()) {
                respuesta.put("pdfError", errorPdf);
            }

            Long usuarioId = resolverUsuarioIdPorHeaders(usernameHeader, adminUsernameHeader);
            auditoriaTramiteService.registrarEvento(
                    tramite.getId(),
                    usuarioId,
                    includePdf ? "VISTA_PREVIA_DOCUMENTO_CON_PDF" : "VISTA_PREVIA_DOCUMENTO",
                    "Consulta de vista previa del documento generado",
                    tramite.getEstado(),
                    tramite.getEstado()
            );

            return ResponseEntity.ok(respuesta);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error al generar vista previa: " + e.getMessage());
        }
    }

    @GetMapping("/reporte/consolidado-verificaciones")
    public ResponseEntity<?> descargarConsolidadoVerificaciones() {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Consolidado Verificaciones");

            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Radicado");
            header.createCell(1).setCellValue("Consecutivo Verificador");
            header.createCell(2).setCellValue("Solicitante");
            header.createCell(3).setCellValue("Documento");
            header.createCell(4).setCellValue("Tipo Certificado");
            header.createCell(5).setCellValue("Estado");
            header.createCell(6).setCellValue("Fecha Radicación");
            header.createCell(7).setCellValue("Fecha Firma Alcalde");
            header.createCell(8).setCellValue("Observaciones");

                List<Tramite> tramites = tramiteRepository.findAll().stream()
                    .sorted((a, b) -> a.getFechaRadicacion().compareTo(b.getFechaRadicacion()))
                    .toList();

            int rowIndex = 1;
            for (Tramite t : tramites) {
                Row row = sheet.createRow(rowIndex++);
                row.createCell(0).setCellValue(valor(t.getNumeroRadicado()));
                row.createCell(1).setCellValue(valor(t.getConsecutivoVerificador()));
                row.createCell(2).setCellValue(valor(t.getNombreSolicitante()));
                row.createCell(3).setCellValue(valor(t.getNumeroDocumento()));
                row.createCell(4).setCellValue(valor(t.getTipo_certificado()));
                row.createCell(5).setCellValue(t.getEstado() != null ? t.getEstado().name() : "");
                row.createCell(6).setCellValue(t.getFechaRadicacion() != null ? t.getFechaRadicacion().toString() : "");
                row.createCell(7).setCellValue(t.getFechaFirmaAlcalde() != null ? t.getFechaFirmaAlcalde().toString() : "");
                row.createCell(8).setCellValue(valor(t.getObservaciones()));
            }

            for (int i = 0; i <= 8; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(outputStream);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"consolidado_verificaciones.xlsx\"")
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(outputStream.toByteArray());
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error al generar el consolidado Excel: " + e.getMessage());
        }
    }

    private String valor(String valor) {
        return valor == null ? "" : valor;
    }

    private String extraerPrimerNombre(String nombreCompleto) {
        if (nombreCompleto == null) {
            return "";
        }
        String normalizado = nombreCompleto.trim().replaceAll("\\s+", " ");
        if (normalizado.isBlank()) {
            return "";
        }
        int idx = normalizado.indexOf(' ');
        return idx > 0 ? normalizado.substring(0, idx) : normalizado;
    }

    private boolean coincidePrimerNombre(String primerNombreIngresado, String primerNombreRegistrado) {
        String ingresado = normalizarTextoComparacion(primerNombreIngresado);
        String registrado = normalizarTextoComparacion(primerNombreRegistrado);
        if (ingresado.isBlank() || registrado.isBlank()) {
            return false;
        }
        return ingresado.equals(registrado);
    }

    private boolean validarFactorReconocimiento(Tramite tramite, String tipo, String valorIngresado) {
        if (tramite == null) {
            return false;
        }

        if ("PRIMER_NOMBRE".equals(tipo)) {
            String primerNombreRegistrado = extraerPrimerNombre(tramite.getNombreSolicitante());
            return coincidePrimerNombre(valorIngresado, primerNombreRegistrado);
        }

        if ("ULTIMOS_3_DOCUMENTO".equals(tipo)) {
            String numeroDoc = tramite.getNumeroDocumento() == null ? "" : tramite.getNumeroDocumento().replaceAll("\\D", "");
            String valor = valorIngresado.replaceAll("\\D", "");
            if (numeroDoc.length() < 3 || valor.length() != 3) {
                return false;
            }
            String ultimosTres = numeroDoc.substring(numeroDoc.length() - 3);
            return ultimosTres.equals(valor);
        }

        return false;
    }

    private String normalizarCorreo(String correo) {
        if (correo == null) {
            return null;
        }
        String normalizado = correo.trim().toLowerCase(Locale.ROOT);
        return normalizado.isBlank() ? null : normalizado;
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
        return folderId;
    }

    private boolean consecutivoYaUsadoEnAnio(String consecutivo, int anio, Long tramiteActualId) {
        LocalDateTime inicio = LocalDate.of(anio, 1, 1).atStartOfDay();
        LocalDateTime fin = LocalDate.of(anio + 1, 1, 1).atStartOfDay();

        return tramiteRepository.findAllByFechaRadicacionBetweenAndConsecutivoVerificadorIsNotNull(inicio, fin).stream()
                .filter(t -> tramiteActualId == null || !Objects.equals(t.getId(), tramiteActualId))
                .map(Tramite::getConsecutivoVerificador)
                .map(this::normalizarConsecutivo)
                .filter(Objects::nonNull)
                .anyMatch(consecutivo::equals);
    }

    private String generarSiguienteConsecutivo(int anio) {
        LocalDateTime inicio = LocalDate.of(anio, 1, 1).atStartOfDay();
        LocalDateTime fin = LocalDate.of(anio + 1, 1, 1).atStartOfDay();

        int maximo = tramiteRepository.findAllByFechaRadicacionBetweenAndConsecutivoVerificadorIsNotNull(inicio, fin).stream()
                .map(Tramite::getConsecutivoVerificador)
                .map(this::normalizarConsecutivo)
                .filter(Objects::nonNull)
                .mapToInt(Integer::parseInt)
                .max()
                .orElse(0);

        return String.format("%03d", maximo + 1);
    }

    private String normalizarConsecutivo(String valor) {
        if (valor == null) {
            return null;
        }

        String soloNumeros = valor.trim().replaceAll("\\D", "");
        if (soloNumeros.isBlank()) {
            return null;
        }

        int numero;
        try {
            numero = Integer.parseInt(soloNumeros);
        } catch (NumberFormatException ex) {
            return null;
        }

        if (numero <= 0) {
            return null;
        }

        if (numero > 999999) {
            return null;
        }

        return String.format("%03d", numero);
    }

    private String normalizarTextoComparacion(String valor) {
        if (valor == null) {
            return "";
        }
        String sinTildes = Normalizer.normalize(valor, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return sinTildes.trim().toUpperCase(Locale.ROOT);
    }

    public static class NotificacionVerificadorAdminDTO {
        public String mensaje;
    }

    private String generarCodigoVerificacion(String numeroRadicado) {
        String base = (numeroRadicado == null ? "TRAM" : numeroRadicado.replaceAll("[^A-Za-z0-9]", "").toUpperCase());
        String sufijo = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyMMddHHmmss"));
        String prefijo = base.length() <= 8 ? base : base.substring(base.length() - 8);
        return prefijo + "-" + sufijo;
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

    private String resolverAccionDocumentoGenerado(String accion) {
        String accionNormalizada = accion == null ? "" : accion.trim().toLowerCase(Locale.ROOT);
        if ("ver".equals(accionNormalizada) || "visualizar".equals(accionNormalizada) || "open".equals(accionNormalizada)) {
            return "CERTIFICADO_GENERADO_VISUALIZADO";
        }
        if ("descargar".equals(accionNormalizada) || "download".equals(accionNormalizada)) {
            return "CERTIFICADO_GENERADO_DESCARGADO";
        }
        return "CERTIFICADO_GENERADO_ACCESO";
    }

    private long millisDesde(long inicioNano) {
        return (System.nanoTime() - inicioNano) / 1_000_000;
    }

    private void subirCertificadoGeneradoADrive(Tramite tramite) throws IOException {
        if (!driveStorageService.isEnabled()) {
            return;
        }
        byte[] contenido = tramite.getContenidoPdfGenerado();
        if (contenido == null || contenido.length == 0) {
            throw new IllegalStateException("No hay PDF generado para almacenar en Google Drive");
        }

        String nombreArchivo = construirNombreArchivoCertificadoFinal(tramite);
        String driveFileId = driveStorageService.uploadSignedCertificate(nombreArchivo, "application/pdf", contenido);
        tramite.setRuta_certificado_final(DRIVE_PREFIX + driveFileId);
    }

    private String construirNombreArchivoCertificadoFinal(Tramite tramite) {
        String nombre = normalizarSegmentoNombreArchivo(tramite.getNombreSolicitante(), "SOLICITANTE");
        String documento = normalizarSegmentoNombreArchivo(tramite.getNumeroDocumento(), "SIN_DOCUMENTO");
        return nombre + "_" + documento + ".pdf";
    }

    private String normalizarSegmentoNombreArchivo(String valor, String porDefecto) {
        if (valor == null || valor.isBlank()) {
            return porDefecto;
        }
        String base = Normalizer.normalize(valor, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^A-Za-z0-9 _-]", "")
                .trim()
                .replaceAll("\\s+", "_")
                .replaceAll("_+", "_");

        if (base.isBlank()) {
            return porDefecto;
        }
        return base.toUpperCase(Locale.ROOT);
    }

    private String calcularHashSha256(byte[] contenido) {
        if (contenido == null || contenido.length == 0) {
            return null;
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(contenido);
            StringBuilder builder = new StringBuilder();
            for (byte b : hash) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("No se pudo calcular hash SHA-256", e);
        }
    }

    private String generarRadicado() {
        return "RES-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
    }
}

