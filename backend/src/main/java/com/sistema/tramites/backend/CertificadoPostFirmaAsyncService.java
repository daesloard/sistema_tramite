package com.sistema.tramites.backend;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;


@Service
public class CertificadoPostFirmaAsyncService {

    private static final Logger log = LoggerFactory.getLogger(CertificadoPostFirmaAsyncService.class);

    private final TramiteRepository tramiteRepository;
    private final DocumentoGeneradoService documentoGeneradoService;
    private final CertificadoDriveAsyncService certificadoDriveAsyncService;
    private final EmailService emailService;
    private final AuditoriaTramiteService auditoriaTramiteService;
    private final MeterRegistry meterRegistry;

    public CertificadoPostFirmaAsyncService(TramiteRepository tramiteRepository,
                                            DocumentoGeneradoService documentoGeneradoService,
                                            CertificadoDriveAsyncService certificadoDriveAsyncService,
                                            EmailService emailService,
                                            AuditoriaTramiteService auditoriaTramiteService,
                                            MeterRegistry meterRegistry) {
        this.tramiteRepository = tramiteRepository;
        this.documentoGeneradoService = documentoGeneradoService;
        this.certificadoDriveAsyncService = certificadoDriveAsyncService;
        this.emailService = emailService;
        this.auditoriaTramiteService = auditoriaTramiteService;
        this.meterRegistry = meterRegistry;
    }

    @Async("pdfTaskExecutor")
    public void procesarPostFirma(Long tramiteId) {
        if (tramiteId == null) {
            return;
        }

        long inicio = System.nanoTime();
        String outcome = "success";
        try {
            Tramite tramite = tramiteRepository.findById(tramiteId).orElse(null);
            if (tramite == null) {
                log.warn("No se encontró trámite {} para post-firma asíncrona", tramiteId);
                outcome = "tramite_not_found";
                return;
            }

            boolean verificacionAprobada = resolverDecisionVerificacion(tramite);

            EstadoTramite estadoAnterior = tramite.getEstado();
            if (estadoAnterior == EstadoTramite.FINALIZADO || estadoAnterior == EstadoTramite.RECHAZADO) {
                log.info("Post-firma omitida para trámite {} porque ya está en estado final ({})", tramiteId, estadoAnterior);
                outcome = "already_final_state";
                return;
            }

            if (estadoAnterior != EstadoTramite.EN_FIRMA) {
                log.warn("Se omitió post-firma para trámite {} porque no está EN_FIRMA (estado={})",
                        tramiteId, estadoAnterior);
                outcome = "invalid_state";
                return;
            }

            if (tramite.getFirmaAlcalde() == null || tramite.getFirmaAlcalde().isBlank()) {
                log.warn("Se omitió post-firma para trámite {} porque la firma del alcalde no está registrada", tramiteId);
                outcome = "missing_signature";
                return;
            }

            boolean actualizado = false;
            byte[] contenidoPdf = tramite.getContenidoPdfGenerado();
            String observaciones = tramite.getObservaciones() == null ? "" : tramite.getObservaciones();
            if (!verificacionAprobada || contenidoPdf == null || contenidoPdf.length == 0) {
                documentoGeneradoService.generarYAdjuntarPdf(tramite, verificacionAprobada, observaciones);
                contenidoPdf = tramite.getContenidoPdfGenerado();
                actualizado = true;
            }

            if (contenidoPdf == null || contenidoPdf.length == 0) {
                log.warn("No se pudo generar PDF final para trámite {}. Se omite envío de correo.", tramiteId);
                outcome = "pdf_generation_failed";
                return;
            }

            if (tramite.getHashDocumentoGenerado() == null || tramite.getHashDocumentoGenerado().isBlank()) {
                tramite.setHashDocumentoGenerado(HashUtils.sha256Hex(contenidoPdf));
                actualizado = true;
            }

            tramite.setEstado(verificacionAprobada ? EstadoTramite.FINALIZADO : EstadoTramite.RECHAZADO);
            actualizado = true;

            if (actualizado) {
                tramite = tramiteRepository.save(tramite);
                contenidoPdf = tramite.getContenidoPdfGenerado();
            }

            Long usuarioAlcaldeId = (tramite.getUsuarioAlcalde() != null) ? tramite.getUsuarioAlcalde().getId() : null;
            auditoriaTramiteService.registrarEvento(
                tramite.getId(),
                usuarioAlcaldeId,
                "FIRMA_ALCALDE",
                "Documento firmado por alcalde y "
                        + (verificacionAprobada ? "finalizado" : "marcado como rechazado")
                        + " para radicado " + tramite.getNumeroRadicado(),
                estadoAnterior,
                tramite.getEstado()
            );

            certificadoDriveAsyncService.subirCertificadoFirmado(tramite.getId());

            try {
                emailService.enviarDocumentoFinal(
                    tramite.getCorreoElectronico(),
                    tramite.getNombreSolicitante(),
                    tramite.getNumeroRadicado(),
                    verificacionAprobada,
                    observaciones,
                    contenidoPdf,
                    tramite.getNombrePdfGenerado()
                );
            } catch (Exception correoEx) {
                log.warn("El trámite {} se finalizó, pero falló el envío de correo: {}", tramiteId, correoEx.getMessage());
                Counter.builder("tramites.postfirma.email.errors")
                        .description("Errores de envio de correo en post-firma")
                        .register(meterRegistry)
                        .increment();
            }

            long duracionMs = (System.nanoTime() - inicio) / 1_000_000;
            log.info("Post-firma asíncrona completada. Trámite={} duracion={}ms", tramiteId, duracionMs);
        } catch (Exception ex) {
            outcome = "exception";
            long duracionMs = (System.nanoTime() - inicio) / 1_000_000;
            log.warn("Falló post-firma asíncrona para trámite {} tras {}ms: {}", tramiteId, duracionMs, ex.getMessage());
        } finally {
            long duracionNanos = System.nanoTime() - inicio;
            Timer.builder("tramites.postfirma.duration")
                    .description("Duracion de procesamiento post-firma")
                    .tag("outcome", outcome)
                    .register(meterRegistry)
                    .record(duracionNanos, TimeUnit.NANOSECONDS);

            Counter.builder("tramites.postfirma.total")
                    .description("Total de ejecuciones post-firma")
                    .tag("outcome", outcome)
                    .register(meterRegistry)
                    .increment();
        }
    }

    private boolean resolverDecisionVerificacion(Tramite tramite) {
        if (tramite == null) {
            return true;
        }

        if (tramite.getVerificacionAprobada() != null) {
            return Boolean.TRUE.equals(tramite.getVerificacionAprobada());
        }

        return tramite.getEstado() != EstadoTramite.RECHAZADO;
    }

}