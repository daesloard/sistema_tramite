package com.sistema.tramites.backend;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
public class NotificacionUsuarioService {

    private static final Logger logger = LoggerFactory.getLogger(NotificacionUsuarioService.class);

    private final NotificacionUsuarioRepository notificacionUsuarioRepository;
    private final WebPushService webPushService;

    public NotificacionUsuarioService(NotificacionUsuarioRepository notificacionUsuarioRepository,
                                     WebPushService webPushService) {
        this.notificacionUsuarioRepository = notificacionUsuarioRepository;
        this.webPushService = webPushService;
    }

    public void crearNotificacion(Long usuarioId,
                                  Long tramiteId,
                                  String titulo,
                                  String mensaje,
                                  String tipo) {
        if (usuarioId == null || titulo == null || titulo.isBlank() || mensaje == null || mensaje.isBlank()) {
            return;
        }

        try {
            NotificacionUsuario notificacion = new NotificacionUsuario();
            notificacion.setUsuarioId(usuarioId);
            notificacion.setTramiteId(tramiteId);
            notificacion.setTitulo(titulo.trim());
            notificacion.setMensaje(mensaje.trim());
            notificacion.setTipo(normalizarTipo(tipo));
            notificacion.setLeida(false);
            notificacion.setFechaCreacion(LocalDateTime.now());
            notificacionUsuarioRepository.save(notificacion);

            webPushService.enviarNotificacionAUsuario(
                    usuarioId,
                    notificacion.getTitulo(),
                    notificacion.getMensaje(),
                    notificacion.getTipo(),
                    notificacion.getTramiteId()
            );
        } catch (Exception ex) {
            logger.warn("No se pudo crear notificación para usuario {}: {}", usuarioId, ex.getMessage());
        }
    }

    public void crearParaUsuarios(Collection<Long> usuariosIds,
                                  Long tramiteId,
                                  String titulo,
                                  String mensaje,
                                  String tipo) {
        if (usuariosIds == null || usuariosIds.isEmpty()) {
            return;
        }

        usuariosIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .forEach(usuarioId -> crearNotificacion(usuarioId, tramiteId, titulo, mensaje, tipo));
    }

    public List<NotificacionUsuario> listarPorUsuario(Long usuarioId) {
        if (usuarioId == null) {
            return List.of();
        }
        return notificacionUsuarioRepository.findTop100ByUsuarioIdOrderByFechaCreacionDesc(usuarioId);
    }

    public long contarNoLeidas(Long usuarioId) {
        if (usuarioId == null) {
            return 0;
        }
        return notificacionUsuarioRepository.countByUsuarioIdAndLeidaFalse(usuarioId);
    }

    public boolean marcarLeida(Long notificacionId, Long usuarioId) {
        if (notificacionId == null || usuarioId == null) {
            return false;
        }

        return notificacionUsuarioRepository.findByIdAndUsuarioId(notificacionId, usuarioId)
                .map(notificacion -> {
                    if (Boolean.TRUE.equals(notificacion.getLeida())) {
                        return true;
                    }
                    notificacion.setLeida(true);
                    notificacion.setFechaLectura(LocalDateTime.now());
                    notificacionUsuarioRepository.save(notificacion);
                    return true;
                })
                .orElse(false);
    }

    public int marcarTodasLeidas(Long usuarioId) {
        if (usuarioId == null) {
            return 0;
        }

        List<NotificacionUsuario> pendientes = notificacionUsuarioRepository.findByUsuarioIdAndLeidaFalse(usuarioId);
        if (pendientes.isEmpty()) {
            return 0;
        }

        LocalDateTime ahora = LocalDateTime.now();
        pendientes.forEach(notificacion -> {
            notificacion.setLeida(true);
            notificacion.setFechaLectura(ahora);
        });
        notificacionUsuarioRepository.saveAll(pendientes);
        return pendientes.size();
    }

    private String normalizarTipo(String tipo) {
        String valor = tipo == null ? "INFO" : tipo.trim().toUpperCase(Locale.ROOT);
        return switch (valor) {
            case "SUCCESS", "WARNING", "ERROR", "INFO" -> valor;
            default -> "INFO";
        };
    }
}
