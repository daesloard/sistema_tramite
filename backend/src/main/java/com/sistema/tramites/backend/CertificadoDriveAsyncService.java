package com.sistema.tramites.backend;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class CertificadoDriveAsyncService {

    private static final Logger log = LoggerFactory.getLogger(CertificadoDriveAsyncService.class);
    private static final String DRIVE_PREFIX = "drive:";

    private final TramiteRepository tramiteRepository;
    private final DriveStorageService driveStorageService;
    private final AuditoriaTramiteService auditoriaTramiteService;

    public CertificadoDriveAsyncService(TramiteRepository tramiteRepository,
                                        DriveStorageService driveStorageService,
                                        AuditoriaTramiteService auditoriaTramiteService) {
        this.tramiteRepository = tramiteRepository;
        this.driveStorageService = driveStorageService;
        this.auditoriaTramiteService = auditoriaTramiteService;
    }

    @Async("driveTaskExecutor")
    public void subirCertificadoFirmado(Long tramiteId) {
        if (tramiteId == null) {
            return;
        }

        if (!driveStorageService.isEnabled()) {
            return;
        }

        long inicio = System.nanoTime();
        try {
            Tramite tramite = tramiteRepository.findById(tramiteId).orElse(null);
            if (tramite == null) {
                log.warn("No se encontró trámite {} para carga asíncrona en Drive", tramiteId);
                return;
            }

            if (tramite.getRuta_certificado_final() != null && tramite.getRuta_certificado_final().startsWith(DRIVE_PREFIX)) {
                return;
            }

            byte[] contenido = tramite.getContenidoPdfGenerado();
            if (contenido == null || contenido.length == 0) {
                log.warn("No hay PDF generado para cargar a Drive. Trámite={}", tramiteId);
                return;
            }

            String nombreArchivo = construirNombreArchivoCertificadoFinal(tramite);
            String driveFileId = driveStorageService.uploadSignedCertificate(nombreArchivo, "application/pdf", contenido);
            tramite.setRuta_certificado_final(DRIVE_PREFIX + driveFileId);
            tramiteRepository.save(tramite);

            auditoriaTramiteService.registrarEventoInmediato(
                    tramite.getId(),
                    null,
                    "POST_FIRMA_DRIVE_OK",
                    "Certificado final cargado a Drive para radicado " + tramite.getNumeroRadicado(),
                    tramite.getEstado(),
                    tramite.getEstado()
            );

            long duracionMs = (System.nanoTime() - inicio) / 1_000_000;
            log.info("Carga asíncrona a Drive completada. Trámite={} DriveFileId={} Duración={}ms", tramiteId, driveFileId, duracionMs);
        } catch (Exception ex) {
            long duracionMs = (System.nanoTime() - inicio) / 1_000_000;
            log.warn("Falló carga asíncrona a Drive para trámite {} tras {}ms: {}", tramiteId, duracionMs, ex.getMessage());
            auditoriaTramiteService.registrarEventoInmediato(
                    tramiteId,
                    null,
                    "POST_FIRMA_DRIVE_ERROR",
                    "Falló carga de certificado a Drive para trámite " + tramiteId + ": " + ex.getMessage(),
                    null,
                    null
            );
        }
    }

    private String construirNombreArchivoCertificadoFinal(Tramite tramite) {
        String nombre = normalizarSegmentoNombreArchivo(tramite.getNombreSolicitante(), "SOLICITANTE");
        String documento = normalizarSegmentoNombreArchivo(tramite.getNumeroDocumento(), "SIN_DOCUMENTO");
        return nombre + "_" + documento + ".pdf";
    }

    private String normalizarSegmentoNombreArchivo(String valor, String porDefecto) {
        if (valor == null || valor.isBlank()) {
            return porDefecto;
        }
        String base = java.text.Normalizer.normalize(valor, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^A-Za-z0-9 _-]", "")
                .trim()
                .replaceAll("\\s+", "_")
                .replaceAll("_+", "_");

        if (base.isBlank()) {
            return porDefecto;
        }
        return base.toUpperCase(java.util.Locale.ROOT);
    }
}
