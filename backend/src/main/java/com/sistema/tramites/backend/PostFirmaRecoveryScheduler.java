package com.sistema.tramites.backend;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class PostFirmaRecoveryScheduler {

    private static final Logger log = LoggerFactory.getLogger(PostFirmaRecoveryScheduler.class);

    private final TramiteRepository tramiteRepository;
    private final CertificadoPostFirmaAsyncService certificadoPostFirmaAsyncService;
    private final AuditoriaTramiteService auditoriaTramiteService;
    private final MeterRegistry meterRegistry;
    private final long minAgeSeconds;

    public PostFirmaRecoveryScheduler(TramiteRepository tramiteRepository,
                                      CertificadoPostFirmaAsyncService certificadoPostFirmaAsyncService,
                                      AuditoriaTramiteService auditoriaTramiteService,
                                      @Value("${app.postfirma.recovery.min-age-seconds:120}") long minAgeSeconds,
                                      MeterRegistry meterRegistry) {
        this.tramiteRepository = tramiteRepository;
        this.certificadoPostFirmaAsyncService = certificadoPostFirmaAsyncService;
        this.auditoriaTramiteService = auditoriaTramiteService;
        this.meterRegistry = meterRegistry;
        this.minAgeSeconds = minAgeSeconds;
    }

    @Scheduled(
            fixedDelayString = "${app.postfirma.recovery.fixed-delay-ms:180000}",
            initialDelayString = "${app.postfirma.recovery.initial-delay-ms:90000}"
    )
    public void recuperarPostFirmaPendiente() {
        LocalDateTime fechaLimite = LocalDateTime.now()
            .minusSeconds(Math.max(30, minAgeSeconds));

        List<Tramite> pendientes = tramiteRepository
                .findTop100ByEstadoAndFirmaAlcaldeIsNotNullAndFechaFirmaAlcaldeBeforeOrderByFechaFirmaAlcaldeAsc(
                        EstadoTramite.EN_FIRMA,
                        fechaLimite
                );

        if (pendientes.isEmpty()) {
            return;
        }

        for (Tramite tramite : pendientes) {
            try {
            certificadoPostFirmaAsyncService.procesarPostFirmaInmediato(tramite.getId());
                auditoriaTramiteService.registrarEventoInmediato(
                        tramite.getId(),
                        null,
                        "POST_FIRMA_RECOVERY_ENCOLADA",
                "Recuperador ejecutó post-firma pendiente para radicado " + tramite.getNumeroRadicado(),
                        tramite.getEstado(),
                        tramite.getEstado()
                );
                Counter.builder("tramites.postfirma.recovery.total")
                        .description("Reencolados de post-firma por recuperador")
                        .tag("outcome", "scheduled")
                        .register(meterRegistry)
                        .increment();
            } catch (Exception ex) {
                log.warn("No se pudo re-encolar post-firma para trámite {}: {}", tramite.getId(), ex.getMessage());
                Counter.builder("tramites.postfirma.recovery.total")
                        .description("Reencolados de post-firma por recuperador")
                        .tag("outcome", "error")
                        .register(meterRegistry)
                        .increment();
            }
        }

        log.info("Recuperador post-firma revisó {} trámite(s) pendiente(s)", pendientes.size());
    }
}
