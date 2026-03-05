package com.sistema.tramites.backend;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class FirmaAlcaldeAsyncService {

    private static final Logger log = LoggerFactory.getLogger(FirmaAlcaldeAsyncService.class);

    private final UsuarioRepository usuarioRepository;
    private final NotificacionUsuarioService notificacionUsuarioService;

    public FirmaAlcaldeAsyncService(UsuarioRepository usuarioRepository,
                                    NotificacionUsuarioService notificacionUsuarioService) {
        this.usuarioRepository = usuarioRepository;
        this.notificacionUsuarioService = notificacionUsuarioService;
    }

    @Async("auditTaskExecutor")
    public void notificarFirmaInterna(Long tramiteId, String numeroRadicado, String usernameAlcalde) {
        if (tramiteId == null || numeroRadicado == null || numeroRadicado.isBlank()) {
            return;
        }

        try {
            Set<Long> destinatariosFirma = java.util.stream.Stream.concat(
                            usuarioRepository.findAllByRolAndActivoTrue(RolUsuario.ADMINISTRADOR).stream(),
                            usuarioRepository.findAllByRolAndActivoTrue(RolUsuario.VERIFICADOR).stream()
                    )
                    .map(Usuario::getId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            String alcalde = (usernameAlcalde == null || usernameAlcalde.isBlank()) ? "alcalde" : usernameAlcalde;
            notificacionUsuarioService.crearParaUsuarios(
                    destinatariosFirma,
                    tramiteId,
                    "Certificado firmado por alcalde",
                    "El trámite " + numeroRadicado + " fue firmado por " + alcalde + " y está en procesamiento final.",
                    "SUCCESS"
            );
        } catch (Exception ex) {
            log.warn("No se pudo programar notificación interna de firma para trámite {}: {}", tramiteId, ex.getMessage());
        }
    }
}
