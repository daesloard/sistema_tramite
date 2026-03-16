package com.sistema.tramites.backend.tramite;

import com.sistema.tramites.backend.auditoria.AuditoriaTramiteService;
import com.sistema.tramites.backend.documento.DriveStorageService;
import com.sistema.tramites.backend.notificacion.NotificacionUsuarioService;
import com.sistema.tramites.backend.tramite.dto.SolicitudCertificadoResidenciaDTO;
import com.sistema.tramites.backend.usuario.RolUsuario;
import com.sistema.tramites.backend.usuario.Usuario;
import com.sistema.tramites.backend.usuario.UsuarioRepository;
import com.sistema.tramites.backend.util.EmailService;
import com.sistema.tramites.backend.util.TramiteUtils;
import com.sistema.tramites.backend.util.WorkingDayCalculator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.*;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class RadicacionService {

    private static final Logger logger = LoggerFactory.getLogger(RadicacionService.class);

    private final TramiteRepository tramiteRepository;
    private final WorkingDayCalculator workingDayCalculator;
    private final EmailService emailService;
    private final UsuarioRepository usuarioRepository;
    private final DriveStorageService driveStorageService;
    private final AuditoriaTramiteService auditoriaTramiteService;
    private final NotificacionUsuarioService notificacionUsuarioService;
    private final TramiteUtils tramiteUtils;

    public RadicacionService(TramiteRepository tramiteRepository,
                             WorkingDayCalculator workingDayCalculator,
                             EmailService emailService,
                             UsuarioRepository usuarioRepository,
                             DriveStorageService driveStorageService,
                             AuditoriaTramiteService auditoriaTramiteService,
                             NotificacionUsuarioService notificacionUsuarioService,
                             TramiteUtils tramiteUtils) {
        this.tramiteRepository = tramiteRepository;
        this.workingDayCalculator = workingDayCalculator;
        this.emailService = emailService;
        this.usuarioRepository = usuarioRepository;
        this.driveStorageService = driveStorageService;
        this.auditoriaTramiteService = auditoriaTramiteService;
        this.notificacionUsuarioService = notificacionUsuarioService;
        this.tramiteUtils = tramiteUtils;
    }

    @Transactional
    public Map<String, Object> radicacionCertificadoResidencia(SolicitudCertificadoResidenciaDTO solicitud) {
        Tramite tramite = new Tramite();
        tramite.setNumeroRadicado(tramiteUtils.generarRadicado());
        tramite.setNombreSolicitante(solicitud.getNombre() != null ? solicitud.getNombre().trim().toUpperCase() : null);
        tramite.setTipoTramite("CERTIFICADO_RESIDENCIA");
        tramite.setFechaRadicacion(LocalDateTime.now());
        tramite.setEstado(EstadoTramite.RADICADO);

        tramite.setTipoDocumento(solicitud.getTipoDocumento());
        String numeroDocumento = solicitud.getNumeroDocumento() == null ? "" : solicitud.getNumeroDocumento().trim();
        if (!numeroDocumento.matches("\\d+")) {
            throw new IllegalArgumentException("El numero de documento solo debe contener digitos");
        }
        tramite.setNumeroDocumento(numeroDocumento);
        tramite.setLugarExpedicionDocumento(solicitud.getLugarExpedicionDocumento());
        tramite.setDireccionResidencia(solicitud.getDireccionResidencia());
        tramite.setBarrioResidencia(solicitud.getBarrioResidencia());
        tramite.setTelefono(solicitud.getTelefono());
        tramite.setCorreoElectronico(tramiteUtils.normalizarCorreo(solicitud.getCorreoElectronico()));
        tramite.setTipo_certificado(solicitud.getTipo_certificado());

        LocalDate fechaVencimiento = workingDayCalculator.calcularFechaVencimiento(LocalDate.now(), 10);
        tramite.setFechaVencimiento(fechaVencimiento);

        LocalDate inicioVigencia = tramiteUtils.obtenerFechaRadicacion(tramite.getFechaRadicacion());
        tramite.setFechaVigencia(workingDayCalculator.calcularFechaVigencia(inicioVigencia));

        Tramite guardado = tramiteRepository.save(tramite);

        procesarDocumentosRadicacionInline(guardado, solicitud);

        auditoriaTramiteService.registrarEvento(
                guardado.getId(), null, "RADICACION_CREADA",
                "Se radica solicitud " + guardado.getNumeroRadicado() + " con documentos procesados", null, guardado.getEstado()
        );

        List<Long> verificadoresIds = usuarioRepository.findAllByRolAndActivoTrue(RolUsuario.VERIFICADOR)
                .stream().map(Usuario::getId).filter(Objects::nonNull).distinct().collect(Collectors.toList());

        notificacionUsuarioService.crearParaUsuarios(
                verificadoresIds, guardado.getId(), "Nueva solicitud radicada",
                "Se radicó la solicitud " + guardado.getNumeroRadicado() + " a nombre de " + guardado.getNombreSolicitante(), "INFO"
        );

        emailService.enviarConfirmacionRadicacion(
                guardado.getCorreoElectronico(), solicitud.getNombre(), guardado.getNumeroRadicado(),
                fechaVencimiento, guardado
        );

        emailService.enviarNotificacionVerificador(
                "verificador@municipio.gov.co", guardado.getNumeroRadicado(), solicitud.getNombre()
        );

        Map<String, Object> respuesta = new HashMap<>();
        respuesta.put("tramiteId", guardado.getId());
        respuesta.put("numeroRadicado", guardado.getNumeroRadicado());
        respuesta.put("fechaSolicitud", guardado.getFechaRadicacion());
        respuesta.put("fechaVencimiento", fechaVencimiento);
        respuesta.put("estado", "RADICADO");
        respuesta.put("driveHabilitado", driveStorageService.isEnabled());
        respuesta.put("driveFolderId", guardado.getDriveFolderId());
        respuesta.put("mensaje", "Solicitud radicada exitosamente con documentos procesados");

        return respuesta;
    }

    private void procesarDocumentosRadicacionInline(Tramite tramite, SolicitudCertificadoResidenciaDTO solicitud) {
        boolean usarDrive = driveStorageService.isEnabled();
        String driveFolderId = tramite.getDriveFolderId();
        if (usarDrive && (driveFolderId == null || driveFolderId.isBlank())) {
            try {
                int anio = (tramite.getFechaRadicacion() != null) ? tramite.getFechaRadicacion().getYear() : Year.now().getValue();
                String baseIdentificacion = (tramite.getNumeroDocumento() != null && !tramite.getNumeroDocumento().isBlank()) ? tramite.getNumeroDocumento() : tramite.getNumeroRadicado();
                driveFolderId = driveStorageService.createSolicitudFolderByDocumento(baseIdentificacion, anio);
                tramite.setDriveFolderId(driveFolderId);
            } catch (Exception ex) {
                logger.warn("Drive folder creation failed, using BD: {}", ex.getMessage());
                usarDrive = false;
            }
        }

        // Process identidad
        if (solicitud.getDocumento_identidad_base64() != null && !solicitud.getDocumento_identidad_base64().trim().isEmpty()) {
            byte[] contenido = Base64.getDecoder().decode(solicitud.getDocumento_identidad_base64());
            String nombre = solicitud.getDocumento_identidad_nombre() != null ? solicitud.getDocumento_identidad_nombre() : "identidad_" + tramite.getId();
            String tipo = solicitud.getDocumento_identidad_tipo() != null ? solicitud.getDocumento_identidad_tipo() : "application/pdf";

            tramite.setNombreArchivoIdentidad(nombre);
            tramite.setTipoContenidoIdentidad(tipo);
            if (usarDrive && driveFolderId != null) {
                try {
                    String driveId = driveStorageService.uploadFileToFolder(nombre, tipo, contenido, driveFolderId);
                    tramite.setRuta_documento_identidad("drive:" + driveId);
                    auditoriaTramiteService.registrarEvento(tramite.getId(), null, "DOCUMENTO_CARGADO_IDENTIDAD", "Identidad subida a Drive", null, null);
                } catch (Exception ex) {
                    logger.warn("Drive upload failed for identidad, using BD: {}", ex.getMessage());
                    tramite.setContenidoDocumentoIdentidad(contenido);
                }
            } else {
                tramite.setContenidoDocumentoIdentidad(contenido);
            }
        }

        // Process solicitud
        if (solicitud.getDocumento_solicitud_base64() != null && !solicitud.getDocumento_solicitud_base64().trim().isEmpty()) {
            byte[] contenido = Base64.getDecoder().decode(solicitud.getDocumento_solicitud_base64());
            String nombre = solicitud.getDocumento_solicitud_nombre() != null ? solicitud.getDocumento_solicitud_nombre() : "solicitud_" + tramite.getId();
            String tipo = solicitud.getDocumento_solicitud_tipo() != null ? solicitud.getDocumento_solicitud_tipo() : "application/pdf";

            tramite.setNombreArchivoSolicitud(nombre);
            tramite.setTipoContenidoSolicitud(tipo);
            if (usarDrive && driveFolderId != null) {
                try {
                    String driveId = driveStorageService.uploadFileToFolder(nombre, tipo, contenido, driveFolderId);
                    tramite.setRuta_documento_solicitud("drive:" + driveId);
                    auditoriaTramiteService.registrarEvento(tramite.getId(), null, "DOCUMENTO_CARGADO_SOLICITUD", "Solicitud subida a Drive", null, null);
                } catch (Exception ex) {
                    logger.warn("Drive upload failed for solicitud, using BD: {}", ex.getMessage());
                    tramite.setContenidoDocumentoSolicitud(contenido);
                }
            } else {
                tramite.setContenidoDocumentoSolicitud(contenido);
            }
        }

        // Process certificado
        if (solicitud.getCertificado_base64() != null && !solicitud.getCertificado_base64().trim().isEmpty()) {
            byte[] contenido = Base64.getDecoder().decode(solicitud.getCertificado_base64());
            String nombre = solicitud.getCertificado_nombre() != null ? solicitud.getCertificado_nombre() : "certificado_" + tramite.getId();
            String tipo = solicitud.getCertificado_tipo() != null ? solicitud.getCertificado_tipo() : "application/pdf";
            String tipoCert = solicitud.getTipo_certificado();

            if (usarDrive && driveFolderId != null) {
                try {
                    String driveId = driveStorageService.uploadFileToFolder(nombre, tipo, contenido, driveFolderId);
                    String ruta = "drive:" + driveId;
                    setCertificadoRuta(tramite, ruta, tipoCert);
                    auditoriaTramiteService.registrarEvento(tramite.getId(), null, "DOCUMENTO_CARGADO_CERTIFICADO", tipoCert + " subida a Drive", null, null);
                } catch (Exception ex) {
                    logger.warn("Drive upload failed for certificado, using BD: {}", ex.getMessage());
                    setCertificadoBlob(tramite, contenido, nombre, tipo, tipoCert);
                }
            } else {
                setCertificadoBlob(tramite, contenido, nombre, tipo, tipoCert);
            }
        }

        tramiteRepository.save(tramite);
    }

    private void setCertificadoRuta(Tramite tramite, String ruta, String tipoCert) {
        switch (tipoCert.toUpperCase()) {
            case "SISBEN":
                tramite.setRuta_certificado_sisben(ruta);
                break;
            case "ELECTORAL":
                tramite.setRuta_certificado_electoral(ruta);
                break;
            case "JAC":
            case "RESIDENCIA":
                tramite.setRuta_certificado(ruta);
                break;
        }
    }

    private void setCertificadoBlob(Tramite tramite, byte[] contenido, String nombre, String tipo, String tipoCert) {
        switch (tipoCert.toUpperCase()) {
            case "SISBEN":
                tramite.setContenidoCertificadoSisben(contenido);
                tramite.setNombreArchivoSisben(nombre);
                tramite.setTipoContenidoSisben(tipo);
                break;
            case "ELECTORAL":
                tramite.setContenidoCertificadoElectoral(contenido);
                tramite.setNombreArchivoElectoral(nombre);
                tramite.setTipoContenidoElectoral(tipo);
                break;
            case "JAC":
            case "RESIDENCIA":
                tramite.setContenidoDocumentoResidencia(contenido);
                tramite.setNombreArchivoResidencia(nombre);
                tramite.setTipoContenidoResidencia(tipo);
                break;
        }
    }
}

