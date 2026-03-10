package com.sistema.tramites.backend.tramite;

import com.sistema.tramites.backend.auditoria.AuditoriaTramiteService;
import com.sistema.tramites.backend.documento.DriveStorageService;
import com.sistema.tramites.backend.notificacion.NotificacionUsuarioService;
import com.sistema.tramites.backend.tramite.async.SolicitudResidenciaAsyncService;
import com.sistema.tramites.backend.tramite.dto.SolicitudCertificadoResidenciaDTO;
import com.sistema.tramites.backend.usuario.RolUsuario;
import com.sistema.tramites.backend.usuario.Usuario;
import com.sistema.tramites.backend.usuario.UsuarioRepository;
import com.sistema.tramites.backend.util.EmailService;
import com.sistema.tramites.backend.util.TramiteUtils;
import com.sistema.tramites.backend.util.WorkingDayCalculator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class RadicacionService {

    private final TramiteRepository tramiteRepository;
    private final WorkingDayCalculator workingDayCalculator;
    private final EmailService emailService;
    private final UsuarioRepository usuarioRepository;
    private final DriveStorageService driveStorageService;
    private final AuditoriaTramiteService auditoriaTramiteService;
    private final NotificacionUsuarioService notificacionUsuarioService;
    private final SolicitudResidenciaAsyncService solicitudResidenciaAsyncService;
    private final TramiteUtils tramiteUtils;

    public RadicacionService(TramiteRepository tramiteRepository,
                             WorkingDayCalculator workingDayCalculator,
                             EmailService emailService,
                             UsuarioRepository usuarioRepository,
                             DriveStorageService driveStorageService,
                             AuditoriaTramiteService auditoriaTramiteService,
                             NotificacionUsuarioService notificacionUsuarioService,
                             SolicitudResidenciaAsyncService solicitudResidenciaAsyncService,
                             TramiteUtils tramiteUtils) {
        this.tramiteRepository = tramiteRepository;
        this.workingDayCalculator = workingDayCalculator;
        this.emailService = emailService;
        this.usuarioRepository = usuarioRepository;
        this.driveStorageService = driveStorageService;
        this.auditoriaTramiteService = auditoriaTramiteService;
        this.notificacionUsuarioService = notificacionUsuarioService;
        this.solicitudResidenciaAsyncService = solicitudResidenciaAsyncService;
        this.tramiteUtils = tramiteUtils;
    }

    @Transactional
    public Map<String, Object> radicacionCertificadoResidencia(SolicitudCertificadoResidenciaDTO solicitud) {
        Tramite tramite = new Tramite();
        tramite.setNumeroRadicado(tramiteUtils.generarRadicado());
        tramite.setNombreSolicitante(solicitud.getNombre() != null ? solicitud.getNombre().trim().toUpperCase(Locale.of("es", "CO")) : null);
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

        solicitudResidenciaAsyncService.procesarDocumentacionRadicada(
                guardado.getId(),
                SolicitudResidenciaAsyncService.DocumentosRadicacionPayload.fromSolicitud(solicitud)
        );

        auditoriaTramiteService.registrarEvento(
                guardado.getId(), null, "RADICACION_CREADA",
                "Se radica solicitud " + guardado.getNumeroRadicado(), null, guardado.getEstado()
        );

        List<Long> verificadoresIds = usuarioRepository.findAllByRolAndActivoTrue(RolUsuario.VERIFICADOR)
                .stream().map(Usuario::getId).filter(Objects::nonNull).distinct().toList();

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
        respuesta.put("almacenamientoIdentidad", "PENDIENTE_ASYNC");
        respuesta.put("almacenamientoSolicitud", "PENDIENTE_ASYNC");
        respuesta.put("almacenamientoCertificado", "PENDIENTE_ASYNC");
        respuesta.put("procesamientoDocumentacion", "EN_COLA");
        respuesta.put("mensaje", "Solicitud radicada exitosamente. La documentacion se esta procesando en segundo plano");

        return respuesta;
    }
}
