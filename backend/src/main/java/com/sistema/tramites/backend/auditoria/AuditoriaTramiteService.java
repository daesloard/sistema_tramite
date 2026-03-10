package com.sistema.tramites.backend.auditoria;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import com.sistema.tramites.backend.tramite.EstadoTramite;

@Service
public class AuditoriaTramiteService {

    private static final Logger logger = LoggerFactory.getLogger(AuditoriaTramiteService.class);

    private final AuditoriaTramiteRepository auditoriaTramiteRepository;

    public AuditoriaTramiteService(AuditoriaTramiteRepository auditoriaTramiteRepository) {
        this.auditoriaTramiteRepository = auditoriaTramiteRepository;
    }

    @Async("auditTaskExecutor")
    public void registrarEvento(Long tramiteId,
                                Long usuarioId,
                                String accion,
                                String descripcion,
                                EstadoTramite estadoAnterior,
                                EstadoTramite estadoNuevo) {
        registrarEventoInterno(tramiteId, usuarioId, accion, descripcion, estadoAnterior, estadoNuevo);
    }

    public void registrarEventoInmediato(Long tramiteId,
                                         Long usuarioId,
                                         String accion,
                                         String descripcion,
                                         EstadoTramite estadoAnterior,
                                         EstadoTramite estadoNuevo) {
        registrarEventoInterno(tramiteId, usuarioId, accion, descripcion, estadoAnterior, estadoNuevo);
    }

    private void registrarEventoInterno(Long tramiteId,
                                        Long usuarioId,
                                        String accion,
                                        String descripcion,
                                        EstadoTramite estadoAnterior,
                                        EstadoTramite estadoNuevo) {
        if (tramiteId == null || accion == null || accion.isBlank()) {
            return;
        }

        try {
            AuditoriaTramite evento = new AuditoriaTramite();
            evento.setTramiteId(tramiteId);
            evento.setUsuarioId(usuarioId);
            evento.setAccion(accion.trim());
            evento.setDescripcion(descripcion);
            evento.setEstadoAnterior(estadoAnterior != null ? estadoAnterior.name() : null);
            evento.setEstadoNuevo(estadoNuevo != null ? estadoNuevo.name() : null);
            evento.setFechaIntegracion(LocalDateTime.now());
            auditoriaTramiteRepository.save(evento);
        } catch (Exception ex) {
            logger.warn("No se pudo registrar auditoría para trámite {}: {}", tramiteId, ex.getMessage());
        }
    }

    public List<AuditoriaTramite> listarPorTramite(Long tramiteId) {
        if (tramiteId == null) {
            return List.of();
        }
        return auditoriaTramiteRepository.findAllByTramiteIdOrderByFechaIntegracionDesc(tramiteId);
    }

    public List<AuditoriaTramite> listarRecientes(LocalDateTime desde) {
        if (desde == null) {
            return auditoriaTramiteRepository.findTop200ByOrderByFechaIntegracionDesc();
        }
        return auditoriaTramiteRepository.findTop200ByFechaIntegracionGreaterThanEqualOrderByFechaIntegracionDesc(desde);
    }
}
