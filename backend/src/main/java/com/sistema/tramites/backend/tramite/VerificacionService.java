package com.sistema.tramites.backend.tramite;

import com.sistema.tramites.backend.auditoria.AuditoriaTramiteService;
import com.sistema.tramites.backend.notificacion.NotificacionUsuarioService;
import com.sistema.tramites.backend.tramite.async.CertificadoPreGeneracionAsyncService;
import com.sistema.tramites.backend.tramite.dto.NotificacionVerificadorAdminDTO;
import com.sistema.tramites.backend.tramite.dto.VerificacionSolicitudDTO;
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
import java.time.Year;
import java.util.*;

@Service
public class VerificacionService {

    private final TramiteRepository tramiteRepository;
    private final WorkingDayCalculator workingDayCalculator;
    private final EmailService emailService;
    private final UsuarioRepository usuarioRepository;
    private final AuditoriaTramiteService auditoriaTramiteService;
    private final NotificacionUsuarioService notificacionUsuarioService;
    private final CertificadoPreGeneracionAsyncService certificadoPreGeneracionAsyncService;
    private final TramiteUtils tramiteUtils;

    public VerificacionService(TramiteRepository tramiteRepository,
                               WorkingDayCalculator workingDayCalculator,
                               EmailService emailService,
                               UsuarioRepository usuarioRepository,
                               AuditoriaTramiteService auditoriaTramiteService,
                               NotificacionUsuarioService notificacionUsuarioService,
                               CertificadoPreGeneracionAsyncService certificadoPreGeneracionAsyncService,
                               TramiteUtils tramiteUtils) {
        this.tramiteRepository = tramiteRepository;
        this.workingDayCalculator = workingDayCalculator;
        this.emailService = emailService;
        this.usuarioRepository = usuarioRepository;
        this.auditoriaTramiteService = auditoriaTramiteService;
        this.notificacionUsuarioService = notificacionUsuarioService;
        this.certificadoPreGeneracionAsyncService = certificadoPreGeneracionAsyncService;
        this.tramiteUtils = tramiteUtils;
    }

    @Transactional
    public Map<String, Object> verificarSolicitud(Long id, VerificacionSolicitudDTO verificacion) {
        Tramite tramite = tramiteRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Trámite no encontrado"));
        EstadoTramite estadoAnterior = tramite.getEstado();
        tramite.setEstado(EstadoTramite.EN_VALIDACION);
        tramite.setObservaciones(verificacion.getObservaciones());

        String usernameVerificador = (verificacion.getUsername() == null || verificacion.getUsername().isBlank()) ? "verificador" : verificacion.getUsername().trim();
        Long usuarioVerificadorId = null;
        Optional<Usuario> usuarioVerificadorOpt = usuarioRepository.findByUsernameAndRolAndActivoTrue(usernameVerificador, RolUsuario.VERIFICADOR);
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
                throw new IllegalArgumentException("Consecutivo inválido. Debe contener solo números (ej: 001)");
            }
            if (consecutivoYaUsadoEnAnio(consecutivoNormalizado, anioConsecutivo, tramite.getId())) {
                throw new IllegalStateException("El consecutivo " + consecutivoNormalizado + " ya fue usado en " + anioConsecutivo);
            }
            tramite.setConsecutivoVerificador(consecutivoNormalizado);
        } else if (tramite.getConsecutivoVerificador() == null || tramite.getConsecutivoVerificador().isBlank()) {
            tramite.setConsecutivoVerificador(generarSiguienteConsecutivo(anioConsecutivo));
        }

        boolean verificacionAprobada = verificacion.isAprobado();
        tramite.setVerificacionAprobada(verificacionAprobada);
        tramite.setEstado(EstadoTramite.EN_FIRMA);
        tramite.setFechaVerificacion(LocalDateTime.now());

        if (verificacionAprobada) {
            LocalDate inicioVigencia = tramiteUtils.obtenerFechaRadicacion(tramite.getFechaRadicacion());
            tramite.setFechaVigencia(workingDayCalculator.calcularFechaVigencia(inicioVigencia));
        }

        if (tramite.getCodigoVerificacion() == null || tramite.getCodigoVerificacion().isBlank()) {
            tramite.setCodigoVerificacion(tramiteUtils.generarCodigoVerificacion(tramite.getNumeroRadicado()));
        }

        Tramite actualizado = tramiteRepository.save(tramite);
        if (verificacionAprobada) {
            certificadoPreGeneracionAsyncService.pregenerarCertificadoParaFirma(actualizado.getId());
        }

        String accionAuditoria = verificacionAprobada ? "VERIFICACION_APROBADA" : "VERIFICACION_RECHAZADA";
        auditoriaTramiteService.registrarEvento(
                actualizado.getId(), usuarioVerificadorId, accionAuditoria,
                "Verificación sobre radicado " + actualizado.getNumeroRadicado() + " con consecutivo " + actualizado.getConsecutivoVerificador(),
                estadoAnterior, actualizado.getEstado()
        );

        List<Long> alcaldesIds = usuarioRepository.findAllByRolAndActivoTrue(RolUsuario.ALCALDE)
                .stream().map(Usuario::getId).filter(Objects::nonNull).distinct().toList();

        String decisionTexto = verificacionAprobada ? "aprobado" : "rechazado";
        notificacionUsuarioService.crearParaUsuarios(
                alcaldesIds, actualizado.getId(), "Solicitud lista para firma",
                "El trámite " + actualizado.getNumeroRadicado() + " fue " + decisionTexto + " por " + usernameVerificador + " y está en firma de alcalde.",
                verificacionAprobada ? "INFO" : "WARNING"
        );

        if (!verificacionAprobada) {
            List<Long> adminsIds = usuarioRepository.findAllByRolAndActivoTrue(RolUsuario.ADMINISTRADOR)
                    .stream().map(Usuario::getId).filter(Objects::nonNull).distinct().toList();

            notificacionUsuarioService.crearParaUsuarios(
                    adminsIds, actualizado.getId(), "Solicitud rechazada por verificador",
                    "El trámite " + actualizado.getNumeroRadicado() + " fue rechazado por " + usernameVerificador + " y quedó pendiente de firma de alcalde.",
                    "WARNING"
            );
        }

        Map<String, Object> respuesta = new HashMap<>();
        respuesta.put("tramiteId", actualizado.getId());
        respuesta.put("numeroRadicado", actualizado.getNumeroRadicado());
        respuesta.put("estado", actualizado.getEstado() != null ? actualizado.getEstado().name() : null);
        respuesta.put("fechaFirmaAlcalde", actualizado.getFechaFirmaAlcalde());
        respuesta.put("codigoVerificacion", actualizado.getCodigoVerificacion());
        respuesta.put("verificacionAprobada", actualizado.getVerificacionAprobada());
        respuesta.put("mensaje", "✅ Solicitud verificada y enviada a firma del alcalde");

        return respuesta;
    }

    @Transactional
    public Map<String, Object> notificarVerificadorDesdeAdmin(Long id, NotificacionVerificadorAdminDTO request, String adminUsername) {
        if (adminUsername == null || adminUsername.isBlank()) {
            throw new SecurityException("Debes autenticarte como administrador");
        }
        Usuario admin = usuarioRepository.findByUsernameAndRolAndActivoTrue(adminUsername.trim(), RolUsuario.ADMINISTRADOR)
                .orElseThrow(() -> new SecurityException("Usuario administrador no válido o inactivo"));

        Tramite tramite = tramiteRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Trámite no encontrado"));

        List<Usuario> verificadores = usuarioRepository.findAllByRolAndActivoTrue(RolUsuario.VERIFICADOR);
        List<String> correos = verificadores.stream()
                .map(Usuario::getEmail).filter(c -> c != null && !c.isBlank())
                .map(c -> c.trim().toLowerCase(Locale.ROOT)).distinct().toList();

        if (correos.isEmpty()) {
            throw new IllegalStateException("No hay verificadores activos con correo configurado");
        }

        String mensajeAdmin = (request != null && request.getMensaje() != null) ? request.getMensaje().trim() : "";
        String detalle = mensajeAdmin.isBlank() ? "Gestión solicitada por administrador" : "Gestión solicitada por administrador: " + mensajeAdmin;

        for (String correo : correos) {
            emailService.enviarNotificacionVerificador(correo, tramite.getNumeroRadicado(), tramite.getNombreSolicitante() + " | " + detalle);
        }

        List<Long> verificadoresIds = verificadores.stream().map(Usuario::getId).filter(Objects::nonNull).distinct().toList();
        notificacionUsuarioService.crearParaUsuarios(
                verificadoresIds, tramite.getId(), "Gestión solicitada por administrador",
                "El administrador " + admin.getUsername() + " solicitó gestionar el trámite " + tramite.getNumeroRadicado() + (mensajeAdmin.isBlank() ? "." : ": " + mensajeAdmin), "INFO"
        );

        auditoriaTramiteService.registrarEvento(
                tramite.getId(), admin.getId(), "NOTIFICACION_VERIFICADOR",
                "Administrador " + admin.getUsername() + " notificó verificadores para radicado " + tramite.getNumeroRadicado(),
                tramite.getEstado(), tramite.getEstado()
        );

        Map<String, Object> respuesta = new HashMap<>();
        respuesta.put("ok", true);
        respuesta.put("tramiteId", tramite.getId());
        respuesta.put("numeroRadicado", tramite.getNumeroRadicado());
        respuesta.put("notificados", correos.size());
        respuesta.put("mensaje", "✅ Verificador(es) notificados");
        return respuesta;
    }

    private boolean consecutivoYaUsadoEnAnio(String consecutivo, int anio, Long tramiteActualId) {
        LocalDateTime inicio = LocalDate.of(anio, 1, 1).atStartOfDay();
        LocalDateTime fin = LocalDate.of(anio + 1, 1, 1).atStartOfDay();
        return tramiteRepository.findAllByFechaRadicacionBetweenAndConsecutivoVerificadorIsNotNull(inicio, fin).stream()
                .filter(t -> tramiteActualId == null || !Objects.equals(t.getId(), tramiteActualId))
                .map(Tramite::getConsecutivoVerificador).map(this::normalizarConsecutivo).filter(Objects::nonNull).anyMatch(consecutivo::equals);
    }

    private String generarSiguienteConsecutivo(int anio) {
        LocalDateTime inicio = LocalDate.of(anio, 1, 1).atStartOfDay();
        LocalDateTime fin = LocalDate.of(anio + 1, 1, 1).atStartOfDay();
        int maximo = tramiteRepository.findAllByFechaRadicacionBetweenAndConsecutivoVerificadorIsNotNull(inicio, fin).stream()
                .map(Tramite::getConsecutivoVerificador).map(this::normalizarConsecutivo).filter(Objects::nonNull).mapToInt(Integer::parseInt).max().orElse(0);
        return String.format("%03d", maximo + 1);
    }

    private String normalizarConsecutivo(String valor) {
        if (valor == null || valor.trim().isBlank() || !valor.trim().matches("\\d+")) return null;
        try {
            int num = Integer.parseInt(valor.trim());
            return num > 0 && num <= 999999 ? String.format("%03d", num) : null;
        } catch (NumberFormatException e) { return null; }
    }
}
