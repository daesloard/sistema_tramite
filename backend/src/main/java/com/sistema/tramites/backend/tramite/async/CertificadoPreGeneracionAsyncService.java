package com.sistema.tramites.backend.tramite.async;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import com.sistema.tramites.backend.tramite.EstadoTramite;
import com.sistema.tramites.backend.tramite.Tramite;
import com.sistema.tramites.backend.tramite.TramiteRepository;
import com.sistema.tramites.backend.documento.DocumentoGeneradoService;

@Service
public class CertificadoPreGeneracionAsyncService {

    private static final Logger log = LoggerFactory.getLogger(CertificadoPreGeneracionAsyncService.class);

    private final TramiteRepository tramiteRepository;
    private final DocumentoGeneradoService documentoGeneradoService;

    public CertificadoPreGeneracionAsyncService(TramiteRepository tramiteRepository,
                                                DocumentoGeneradoService documentoGeneradoService) {
        this.tramiteRepository = tramiteRepository;
        this.documentoGeneradoService = documentoGeneradoService;
    }

    @Async("pdfTaskExecutor")
    public void pregenerarCertificadoParaFirma(Long tramiteId) {
        if (tramiteId == null) {
            return;
        }

        long inicio = System.nanoTime();
        try {
            Tramite tramite = tramiteRepository.findById(tramiteId).orElse(null);
            if (tramite == null) {
                return;
            }

            if (tramite.getEstado() != EstadoTramite.EN_FIRMA) {
                return;
            }

            if (tramite.getContenidoPdfGenerado() != null && tramite.getContenidoPdfGenerado().length > 0) {
                return;
            }

            documentoGeneradoService.generarYAdjuntarPdf(tramite, true, "");
            byte[] contenidoGenerado = tramite.getContenidoPdfGenerado();
            if (contenidoGenerado == null || contenidoGenerado.length == 0) {
                return;
            }

            Tramite actual = tramiteRepository.findById(tramiteId).orElse(null);
            if (actual == null) {
                return;
            }

            // Evita sobrescribir firma/estado con una entidad obsoleta cuando hay
            // concurrencia entre pre-generacion y firma del alcalde.
            actual.setContenidoPdfGenerado(contenidoGenerado);
            actual.setNombrePdfGenerado(tramite.getNombrePdfGenerado());
            actual.setMotorPdfGenerado(tramite.getMotorPdfGenerado());
            actual.setHashDocumentoGenerado(calcularHashSha256(contenidoGenerado));
            tramiteRepository.save(actual);

            long duracionMs = (System.nanoTime() - inicio) / 1_000_000;
            log.info("Pre-generación de certificado completada. Trámite={} duracion={}ms", tramiteId, duracionMs);
        } catch (Exception ex) {
            long duracionMs = (System.nanoTime() - inicio) / 1_000_000;
            log.warn("Falló pre-generación de certificado para trámite {} tras {}ms: {}", tramiteId, duracionMs, ex.getMessage());
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
