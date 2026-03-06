package com.sistema.tramites.backend;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Year;
import java.util.Base64;

@Service
public class SolicitudResidenciaAsyncService {

    private static final Logger logger = LoggerFactory.getLogger(SolicitudResidenciaAsyncService.class);
    private static final String DRIVE_PREFIX = "drive:";

    private final TramiteRepository tramiteRepository;
    private final DriveStorageService driveStorageService;
    private final AuditoriaTramiteService auditoriaTramiteService;

    public SolicitudResidenciaAsyncService(TramiteRepository tramiteRepository,
                                           DriveStorageService driveStorageService,
                                           AuditoriaTramiteService auditoriaTramiteService) {
        this.tramiteRepository = tramiteRepository;
        this.driveStorageService = driveStorageService;
        this.auditoriaTramiteService = auditoriaTramiteService;
    }

    @Async("driveTaskExecutor")
    public void procesarDocumentacionRadicada(Long tramiteId, DocumentosRadicacionPayload payload) {
        if (tramiteId == null || payload == null) {
            return;
        }

        long inicio = System.nanoTime();
        try {
            Tramite tramite = tramiteRepository.findById(tramiteId).orElse(null);
            if (tramite == null) {
                logger.warn("No se encontro tramite {} para carga asincrona de documentacion", tramiteId);
                return;
            }

            boolean usarDrive = driveStorageService.isEnabled();
            String driveFolderId = tramite.getDriveFolderId();
            if (usarDrive && (driveFolderId == null || driveFolderId.isBlank())) {
                try {
                    int anio = (tramite.getFechaRadicacion() != null)
                            ? tramite.getFechaRadicacion().getYear()
                            : Year.now().getValue();
                    String baseIdentificacion = (tramite.getNumeroDocumento() != null && !tramite.getNumeroDocumento().isBlank())
                            ? tramite.getNumeroDocumento()
                            : (tramite.getNumeroRadicado() != null ? tramite.getNumeroRadicado() : "solicitud");

                    driveFolderId = driveStorageService.createSolicitudFolderByDocumento(baseIdentificacion, anio);
                    tramite.setDriveFolderId(driveFolderId);
                } catch (Exception ex) {
                    usarDrive = false;
                    logger.warn("No fue posible crear carpeta Drive para tramite {}. Se usara BD: {}", tramiteId, ex.getMessage());
                }
            }

            procesarDocumentoIdentidad(tramite, payload, usarDrive, driveFolderId);
            procesarDocumentoSolicitud(tramite, payload, usarDrive, driveFolderId);
            procesarDocumentoCertificado(tramite, payload, usarDrive, driveFolderId);

            tramiteRepository.save(tramite);

            auditoriaTramiteService.registrarEvento(
                    tramite.getId(),
                    null,
                    "RADICACION_DOCUMENTOS_ASYNC_OK",
                    "Documentacion inicial procesada en segundo plano",
                    tramite.getEstado(),
                    tramite.getEstado()
            );

            long duracionMs = (System.nanoTime() - inicio) / 1_000_000;
            logger.info("Carga asincrona de documentacion completada. Trámite={} duracion={}ms", tramiteId, duracionMs);
        } catch (Exception ex) {
            long duracionMs = (System.nanoTime() - inicio) / 1_000_000;
            logger.warn("Fallo carga asincrona de documentacion para tramite {} tras {}ms: {}", tramiteId, duracionMs, ex.getMessage());
            auditoriaTramiteService.registrarEvento(
                    tramiteId,
                    null,
                    "RADICACION_DOCUMENTOS_ASYNC_ERROR",
                    "Error procesando documentacion inicial en segundo plano: " + ex.getMessage(),
                    null,
                    null
            );
        }
    }

    private void procesarDocumentoIdentidad(Tramite tramite,
                                            DocumentosRadicacionPayload payload,
                                            boolean usarDrive,
                                            String driveFolderId) {
        if (!tieneTexto(payload.documentoIdentidadBase64())) {
            return;
        }

        byte[] contenido = decodificarBase64(payload.documentoIdentidadBase64(), "documento_identidad", tramite.getId());
        if (contenido == null) {
            return;
        }

        String nombre = resolverNombreArchivo(payload.documentoIdentidadNombre(), "documento_identidad");
        String tipo = resolverTipoContenido(payload.documentoIdentidadTipo());

        tramite.setNombreArchivoIdentidad(nombre);
        tramite.setTipoContenidoIdentidad(tipo);

        if (usarDrive && tieneTexto(driveFolderId)) {
            try {
                String driveId = driveStorageService.uploadFileToFolder(nombre, tipo, contenido, driveFolderId);
                tramite.setRuta_documento_identidad(DRIVE_PREFIX + driveId);
                tramite.setContenidoDocumentoIdentidad(null);
                return;
            } catch (Exception ex) {
                logger.warn("No fue posible subir identidad a Drive para tramite {}. Se guarda en BD: {}", tramite.getId(), ex.getMessage());
            }
        }

        tramite.setRuta_documento_identidad(null);
        tramite.setContenidoDocumentoIdentidad(contenido);
    }

    private void procesarDocumentoSolicitud(Tramite tramite,
                                            DocumentosRadicacionPayload payload,
                                            boolean usarDrive,
                                            String driveFolderId) {
        if (!tieneTexto(payload.documentoSolicitudBase64())) {
            return;
        }

        byte[] contenido = decodificarBase64(payload.documentoSolicitudBase64(), "documento_solicitud", tramite.getId());
        if (contenido == null) {
            return;
        }

        String nombre = resolverNombreArchivo(payload.documentoSolicitudNombre(), "documento_solicitud");
        String tipo = resolverTipoContenido(payload.documentoSolicitudTipo());

        tramite.setNombreArchivoSolicitud(nombre);
        tramite.setTipoContenidoSolicitud(tipo);

        if (usarDrive && tieneTexto(driveFolderId)) {
            try {
                String driveId = driveStorageService.uploadFileToFolder(nombre, tipo, contenido, driveFolderId);
                tramite.setRuta_documento_solicitud(DRIVE_PREFIX + driveId);
                tramite.setContenidoDocumentoSolicitud(null);
                return;
            } catch (Exception ex) {
                logger.warn("No fue posible subir solicitud a Drive para tramite {}. Se guarda en BD: {}", tramite.getId(), ex.getMessage());
            }
        }

        tramite.setRuta_documento_solicitud(null);
        tramite.setContenidoDocumentoSolicitud(contenido);
    }

    private void procesarDocumentoCertificado(Tramite tramite,
                                              DocumentosRadicacionPayload payload,
                                              boolean usarDrive,
                                              String driveFolderId) {
        if (!tieneTexto(payload.certificadoBase64())) {
            return;
        }

        byte[] contenido = decodificarBase64(payload.certificadoBase64(), "certificado", tramite.getId());
        if (contenido == null) {
            return;
        }

        String nombre = resolverNombreArchivo(payload.certificadoNombre(), "certificado");
        String tipo = resolverTipoContenido(payload.certificadoTipo());

        String rutaDrive = null;
        if (usarDrive && tieneTexto(driveFolderId)) {
            try {
                String driveId = driveStorageService.uploadFileToFolder(nombre, tipo, contenido, driveFolderId);
                rutaDrive = DRIVE_PREFIX + driveId;
            } catch (Exception ex) {
                logger.warn("No fue posible subir certificado a Drive para tramite {}. Se guarda en BD: {}", tramite.getId(), ex.getMessage());
            }
        }

        // Respaldo generico para flujos que leen certificado de residencia.
        tramite.setNombreArchivoResidencia(nombre);
        tramite.setTipoContenidoResidencia(tipo);
        if (rutaDrive != null) {
            tramite.setRuta_certificado(rutaDrive);
            tramite.setContenidoDocumentoResidencia(null);
        } else {
            tramite.setRuta_certificado(null);
            tramite.setContenidoDocumentoResidencia(contenido);
        }

        String tipoSeleccionado = tieneTexto(payload.tipoCertificado())
                ? payload.tipoCertificado()
                : tramite.getTipo_certificado();

        if ("SISBEN".equalsIgnoreCase(tipoSeleccionado)) {
            tramite.setNombreArchivoSisben(nombre);
            tramite.setTipoContenidoSisben(tipo);
            if (rutaDrive != null) {
                tramite.setRuta_certificado_sisben(rutaDrive);
                tramite.setContenidoCertificadoSisben(null);
            } else {
                tramite.setRuta_certificado_sisben(null);
                tramite.setContenidoCertificadoSisben(contenido);
            }
        } else if ("ELECTORAL".equalsIgnoreCase(tipoSeleccionado)) {
            tramite.setNombreArchivoElectoral(nombre);
            tramite.setTipoContenidoElectoral(tipo);
            if (rutaDrive != null) {
                tramite.setRuta_certificado_electoral(rutaDrive);
                tramite.setContenidoCertificadoElectoral(null);
            } else {
                tramite.setRuta_certificado_electoral(null);
                tramite.setContenidoCertificadoElectoral(contenido);
            }
        }
    }

    private byte[] decodificarBase64(String base64, String tipo, Long tramiteId) {
        try {
            return Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException ex) {
            logger.warn("Base64 invalido para {} en tramite {}: {}", tipo, tramiteId, ex.getMessage());
            return null;
        }
    }

    private String resolverNombreArchivo(String nombreEntrada, String prefijo) {
        if (tieneTexto(nombreEntrada)) {
            return nombreEntrada.trim();
        }
        return prefijo + "_" + System.currentTimeMillis();
    }

    private String resolverTipoContenido(String tipoEntrada) {
        if (tieneTexto(tipoEntrada)) {
            return tipoEntrada.trim();
        }
        return "application/pdf";
    }

    private boolean tieneTexto(String valor) {
        return valor != null && !valor.isBlank();
    }

    public record DocumentosRadicacionPayload(
            String documentoIdentidadBase64,
            String documentoIdentidadNombre,
            String documentoIdentidadTipo,
            String documentoSolicitudBase64,
            String documentoSolicitudNombre,
            String documentoSolicitudTipo,
            String certificadoBase64,
            String certificadoNombre,
            String certificadoTipo,
            String tipoCertificado
    ) {
        public static DocumentosRadicacionPayload fromSolicitud(SolicitudCertificadoResidenciaDTO solicitud) {
            if (solicitud == null) {
                return new DocumentosRadicacionPayload(null, null, null, null, null, null, null, null, null, null);
            }
            return new DocumentosRadicacionPayload(
                    solicitud.getDocumento_identidad_base64(),
                    solicitud.getDocumento_identidad_nombre(),
                    solicitud.getDocumento_identidad_tipo(),
                    solicitud.getDocumento_solicitud_base64(),
                    solicitud.getDocumento_solicitud_nombre(),
                    solicitud.getDocumento_solicitud_tipo(),
                    solicitud.getCertificado_base64(),
                    solicitud.getCertificado_nombre(),
                    solicitud.getCertificado_tipo(),
                    solicitud.getTipo_certificado()
            );
        }
    }
}
