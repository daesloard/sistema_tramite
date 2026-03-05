package com.sistema.tramites.backend;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Service
public class CertificadoPostFirmaAsyncService {

    private static final Logger log = LoggerFactory.getLogger(CertificadoPostFirmaAsyncService.class);

    private final TramiteRepository tramiteRepository;
    private final DocumentoGeneradoService documentoGeneradoService;
    private final CertificadoDriveAsyncService certificadoDriveAsyncService;
    private final EmailService emailService;

    public CertificadoPostFirmaAsyncService(TramiteRepository tramiteRepository,
                                            DocumentoGeneradoService documentoGeneradoService,
                                            CertificadoDriveAsyncService certificadoDriveAsyncService,
                                            EmailService emailService) {
        this.tramiteRepository = tramiteRepository;
        this.documentoGeneradoService = documentoGeneradoService;
        this.certificadoDriveAsyncService = certificadoDriveAsyncService;
        this.emailService = emailService;
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

            if (tramite.getEstado() != EstadoTramite.FINALIZADO) {
                log.warn("Se omitió post-firma para trámite {} porque no está FINALIZADO (estado={})",
                        tramiteId, tramite.getEstado());
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
                tramite.setHashDocumentoGenerado(calcularHashSha256(contenidoPdf));
                actualizado = true;
            }

            if (actualizado) {
                tramite = tramiteRepository.save(tramite);
                contenidoPdf = tramite.getContenidoPdfGenerado();
            }

            certificadoDriveAsyncService.subirCertificadoFirmado(tramite.getId());

            emailService.enviarDocumentoFinal(
                    tramite.getCorreoElectronico(),
                    tramite.getNombreSolicitante(),
                    tramite.getNumeroRadicado(),
                    true,
                    "",
                    contenidoPdf,
                    tramite.getNombrePdfGenerado()
            );

            long duracionMs = (System.nanoTime() - inicio) / 1_000_000;
            log.info("Post-firma asíncrona completada. Trámite={} duracion={}ms", tramiteId, duracionMs);
        } catch (Exception ex) {
            long duracionMs = (System.nanoTime() - inicio) / 1_000_000;
            log.warn("Falló post-firma asíncrona para trámite {} tras {}ms: {}", tramiteId, duracionMs, ex.getMessage());
        }
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
}