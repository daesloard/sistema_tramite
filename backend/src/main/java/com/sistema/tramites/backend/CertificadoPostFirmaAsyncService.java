package com.sistema.tramites.backend;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;


@Service
public class CertificadoPostFirmaAsyncService {

    private static final Logger log = LoggerFactory.getLogger(CertificadoPostFirmaAsyncService.class);

    private final TramiteRepository tramiteRepository;
    private final DocumentoGeneradoService documentoGeneradoService;
    private final CertificadoDriveAsyncService certificadoDriveAsyncService;
    private final EmailService emailService;
    private final AuditoriaTramiteService auditoriaTramiteService;

    public CertificadoPostFirmaAsyncService(TramiteRepository tramiteRepository,
                                            DocumentoGeneradoService documentoGeneradoService,
                                            CertificadoDriveAsyncService certificadoDriveAsyncService,
                                            EmailService emailService,
                                            AuditoriaTramiteService auditoriaTramiteService) {
        this.tramiteRepository = tramiteRepository;
        this.documentoGeneradoService = documentoGeneradoService;
        this.certificadoDriveAsyncService = certificadoDriveAsyncService;
        this.emailService = emailService;
        this.auditoriaTramiteService = auditoriaTramiteService;
    }

    @Async("pdfTaskExecutor")
    public void procesarPostFirma(Long tramiteId) {
        if (tramiteId == null) {
            return;
        }

        long inicio = System.nanoTime();
        try {
            Tramite tramite = tramiteRepository.findById(tramiteId).orElse(null);
            if (tramite == null) {
                log.warn("No se encontró trámite {} para post-firma asíncrona", tramiteId);
                return;
            }

            EstadoTramite estadoAnterior = tramite.getEstado();
            if (estadoAnterior == EstadoTramite.FINALIZADO) {
                log.info("Post-firma omitida para trámite {} porque ya está FINALIZADO", tramiteId);
                return;
            }

            if (estadoAnterior != EstadoTramite.EN_FIRMA) {
                log.warn("Se omitió post-firma para trámite {} porque no está EN_FIRMA (estado={})",
                        tramiteId, estadoAnterior);
                return;
            }

            if (tramite.getFirmaAlcalde() == null || tramite.getFirmaAlcalde().isBlank()) {
                log.warn("Se omitió post-firma para trámite {} porque la firma del alcalde no está registrada", tramiteId);
                return;
            }

            boolean actualizado = false;
            byte[] contenidoPdf = tramite.getContenidoPdfGenerado();
            if (contenidoPdf == null || contenidoPdf.length == 0) {
                documentoGeneradoService.generarYAdjuntarPdf(tramite, true, "");
                contenidoPdf = tramite.getContenidoPdfGenerado();
                actualizado = true;
            }

            if (contenidoPdf == null || contenidoPdf.length == 0) {
                log.warn("No se pudo generar PDF final para trámite {}. Se omite envío de correo.", tramiteId);
                return;
            }

            if (tramite.getHashDocumentoGenerado() == null || tramite.getHashDocumentoGenerado().isBlank()) {
                tramite.setHashDocumentoGenerado(HashUtils.sha256Hex(contenidoPdf));
                actualizado = true;
            }

            tramite.setEstado(EstadoTramite.FINALIZADO);
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
                "Documento firmado por alcalde y finalizado para radicado " + tramite.getNumeroRadicado(),
                estadoAnterior,
                tramite.getEstado()
            );

            certificadoDriveAsyncService.subirCertificadoFirmado(tramite.getId());

            try {
                emailService.enviarDocumentoFinal(
                    tramite.getCorreoElectronico(),
                    tramite.getNombreSolicitante(),
                    tramite.getNumeroRadicado(),
                    true,
                    "",
                    contenidoPdf,
                    tramite.getNombrePdfGenerado()
                );
            } catch (Exception correoEx) {
                log.warn("El trámite {} se finalizó, pero falló el envío de correo: {}", tramiteId, correoEx.getMessage());
            }

            long duracionMs = (System.nanoTime() - inicio) / 1_000_000;
            log.info("Post-firma asíncrona completada. Trámite={} duracion={}ms", tramiteId, duracionMs);
        } catch (Exception ex) {
            long duracionMs = (System.nanoTime() - inicio) / 1_000_000;
            log.warn("Falló post-firma asíncrona para trámite {} tras {}ms: {}", tramiteId, duracionMs, ex.getMessage());
        }
    }

}