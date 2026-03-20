package com.sistema.tramites.backend.tramite;

import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import com.sistema.tramites.backend.util.TramiteUtils;
import java.util.*;

@Service
public class TramiteService {
        // Lógica para obtener la firma en base64
        private String obtenerFirmaBase64(Tramite tramite) {
            // Si la firma es byte[] (preferido)
            try {
                Object firma = tramite.getFirmaAlcalde();
                if (firma instanceof byte[] firmaBytes) {
                    return java.util.Base64.getEncoder().encodeToString(firmaBytes);
                }
                if (firma instanceof String firmaStr) {
                    // Si ya es base64, devolver tal cual
                    return firmaStr;
                }
            } catch (Exception ignored) {}
            return "";
        }

        // Lógica para determinar si el trámite está aprobado
        private boolean esDecisionAprobada(Tramite tramite) {
            // Ejemplo: si tienes un campo verificacionAprobada
            return tramite.getVerificacionAprobada() != null && tramite.getVerificacionAprobada();
        }
    @org.springframework.beans.factory.annotation.Autowired
    private com.sistema.tramites.backend.documento.DocumentoGeneradoService documentoGeneradoService;

    // Limpiar contador de notificaciones por trámite
    private void limpiarNotificacionesPorTramite(Long tramiteId) {
        try {
            var field = com.sistema.tramites.backend.documento.DocumentoGeneradoService.class.getDeclaredField("notificacionesPorTramite");
            field.setAccessible(true);
            var map = (java.util.Map<Long, Integer>) field.get(documentoGeneradoService);
            if (map != null) map.remove(tramiteId);
        } catch (Exception ignored) {}
    }

    public void regenerarPdf(Long tramiteId) {
        Tramite tramite = tramiteRepository.findById(tramiteId)
            .orElseThrow(() -> new IllegalArgumentException("Trámite no encontrado"));
        try {
            // Seleccionar plantilla según tipo de trámite
            String tipo = tramite.getTipoTramite() != null ? tramite.getTipoTramite().trim().toUpperCase() : "";
            String plantilla;
            switch (tipo) {
                case "JUNTA DE ACCION":
                case "CERTIFICADO_RESIDENCIA":
                    plantilla = "CARTA RESIDENCIA JUNTA DE ACCION.docx";
                    break;
                case "REGISTRADURIA NACIONAL":
                    plantilla = "CARTA RESIDENCIA REGISTRADURIA NACIONAL.docx";
                    break;
                case "SISBEN":
                case "CERTIFICADO_SISBEN":
                    plantilla = "CARTA RESIDENCIA SISBEN.docx";
                    break;
                case "NEGATIVA":
                case "CERTIFICADO_NEGATIVO":
                    plantilla = "RESPUESTA NEGATIVA.docx";
                    break;
                default:
                    throw new RuntimeException("Tipo de trámite desconocido: " + tipo);
            }
            java.util.Map<String, String> datos = new java.util.HashMap<>();
            datos.put("consecutivo", tramite.getConsecutivoVerificador() != null ? tramite.getConsecutivoVerificador() : "");
            datos.put("nombreSolicitante", tramite.getNombreSolicitante() != null ? tramite.getNombreSolicitante() : "");
            datos.put("numeroDocumento", tramite.getNumeroDocumento() != null ? tramite.getNumeroDocumento() : "");
            datos.put("lugarExpedicionDocumento", tramite.getLugarExpedicionDocumento() != null ? tramite.getLugarExpedicionDocumento() : "");
            datos.put("direccionResidencia", tramite.getDireccionResidencia() != null ? tramite.getDireccionResidencia() : "");
            LocalDate fechaFirma = tramite.getFechaFirmaAlcalde() != null ? tramite.getFechaFirmaAlcalde().toLocalDate() : LocalDate.now();
            datos.put("dias", String.valueOf(fechaFirma.getDayOfMonth()));
            datos.put("diasLetras", com.sistema.tramites.backend.util.NumeroALetrasUtil.numeroALetras(fechaFirma.getDayOfMonth()));
            datos.put("mesLetras", com.sistema.tramites.backend.util.NumeroALetrasUtil.mesALetras(fechaFirma));
            datos.put("año", String.valueOf(fechaFirma.getYear()));
            datos.put("añoLetra", com.sistema.tramites.backend.util.NumeroALetrasUtil.anioALetras(fechaFirma.getYear()));
            datos.put("firma.jpeg", obtenerFirmaBase64(tramite)); // Para imágenes, se requiere lógica especial
            datos.put("alcalde", tramite.getUsuarioAlcalde() != null ? tramite.getUsuarioAlcalde().getNombreCompleto() : "");
            datos.put("verificador", tramite.getUsuarioVerificador() != null ? tramite.getUsuarioVerificador().getNombreCompleto() : "");
            datos.put("numeroRadico", tramite.getNumeroRadicado() != null ? tramite.getNumeroRadicado() : "");
            datos.put("fechaFirma", tramite.getFechaFirmaAlcalde() != null ? tramite.getFechaFirmaAlcalde().toString() : "");
            datos.put("observacion", tramite.getObservaciones() != null ? tramite.getObservaciones() : "");

            // Puedes agregar lógica para calcular los valores en letras y fechas

            boolean aprobado = esDecisionAprobada(tramite);
            String observacion = tramite.getObservaciones() != null ? tramite.getObservaciones() : "";
            byte[] pdf = documentoGeneradoService.generarPdfDocumento(tramite, aprobado, observacion);
            tramite.setContenidoPdfGenerado(pdf);
            tramite.setNombrePdfGenerado(plantilla.replace(".docx", ".pdf"));
            tramite.setTipoContenidoPdfGenerado("application/pdf");
            tramite.setMotorPdfGenerado("Gotenberg");
            tramite.setHashDocumentoGenerado(com.sistema.tramites.backend.util.HashUtils.sha256Hex(pdf));
        } catch (Exception ex) {
            throw new RuntimeException("Error al generar PDF con Gotenberg", ex);
        }
        tramiteRepository.save(tramite);
        limpiarNotificacionesPorTramite(tramiteId);
    }

    private final TramiteRepository tramiteRepository;
    private final TramiteUtils tramiteUtils;

    public TramiteService(TramiteRepository tramiteRepository, TramiteUtils tramiteUtils) {
        this.tramiteRepository = tramiteRepository;
        this.tramiteUtils = tramiteUtils;
    }

    public List<Map<String, Object>> listarResumen() {
        return tramiteRepository.findAllResumen()
                .stream()
                .map(this::construirResumenTramite)
                .toList();
    }

    public Optional<Tramite> obtenerPorId(Long id) {
        return tramiteRepository.findById(id);
    }

    public Tramite crear(Tramite tramite) {
        if (tramite.getNumeroRadicado() == null || tramite.getNumeroRadicado().isBlank()) {
            tramite.setNumeroRadicado(tramiteUtils.generarRadicado());
        }
        if (tramite.getFechaRadicacion() == null) {
            tramite.setFechaRadicacion(LocalDateTime.now());
        }
        if (tramite.getEstado() == null) {
            tramite.setEstado(EstadoTramite.RADICADO);
        }
        return tramiteRepository.save(tramite);
    }

    public Optional<Tramite> actualizar(Long id, Tramite entrada) {
        return tramiteRepository.findById(id)
                .map(actual -> {
                    actual.setNombreSolicitante(entrada.getNombreSolicitante());
                    actual.setTipoTramite(entrada.getTipoTramite());
                    actual.setDescripcion(entrada.getDescripcion());
                    actual.setEstado(entrada.getEstado() != null ? entrada.getEstado() : actual.getEstado());
                    return tramiteRepository.save(actual);
                });
    }

    public boolean eliminar(Long id) {
        if (!tramiteRepository.existsById(id)) return false;
        tramiteRepository.deleteById(id);
        return true;
    }

    public String generarCodigoVerificacion(String numeroRadicado) {
        return tramiteUtils.generarCodigoVerificacion(numeroRadicado);
    }

    private Map<String, Object> construirResumenTramite(TramiteResumenView tramite) {
        Map<String, Object> item = new HashMap<>();
        item.put("id", tramite.getId());
        item.put("numeroRadicado", tramite.getNumeroRadicado());
        item.put("nombreSolicitante", tramite.getNombreSolicitante());
        item.put("tipoTramite", tramite.getTipoTramite());
        item.put("estado", tramite.getEstado() != null ? tramite.getEstado().name() : null);
        item.put("fechaRadicacion", tramite.getFechaRadicacion());
        item.put("fechaVencimiento", tramite.getFechaVencimiento());
        item.put("fechaVigencia", tramite.getFechaVigencia());
        item.put("fechaVerificacion", tramite.getFechaVerificacion());
        item.put("verificacionAprobada", tramite.getVerificacionAprobada());
        item.put("fechaFirmaAlcalde", tramite.getFechaFirmaAlcalde());
        item.put("tipoDocumento", tramite.getTipoDocumento());
        item.put("numeroDocumento", tramite.getNumeroDocumento());
        item.put("lugarExpedicionDocumento", tramite.getLugarExpedicionDocumento());
        item.put("direccionResidencia", tramite.getDireccionResidencia());
        item.put("barrioResidencia", tramite.getBarrioResidencia());
        item.put("telefono", tramite.getTelefono());
        item.put("correoElectronico", tramite.getCorreoElectronico());
        item.put("tipo_certificado", tramite.getTipo_certificado());
        item.put("observaciones", tramite.getObservaciones());
        item.put("consecutivoVerificador", tramite.getConsecutivoVerificador());
        item.put("ruta_certificado_final", tramite.getRuta_certificado_final());
        item.put("nombrePdfGenerado", tramite.getNombrePdfGenerado());
        item.put("motorPdfGenerado", tramite.getMotorPdfGenerado());
        return item;
    }
}
