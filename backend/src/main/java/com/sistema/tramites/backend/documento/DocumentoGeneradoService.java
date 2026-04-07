package com.sistema.tramites.backend.documento;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class DocumentoGeneradoService {

    @Autowired
    private DocxtemplaterService docxtemplaterService;

    @Autowired
    private Docx4jPdfConverterService docx4jPdfConverterService;

    @Value("${app.pdf.gotenberg.url:}")
    private String gotenbergUrl;

    @Value("${app.pdf.gotenberg.enabled:false}")
    private boolean gotenbergEnabled;

    @Value("${app.pdf.gotenberg.max-attempts:3}")
    private int gotenbergMaxAttempts;

    @Value("${app.pdf.gotenberg.retry-delay-ms:800}")
    private long gotenbergRetryDelayMs;

    /**
     * Genera PDF a partir de plantilla DOCX con marcadores {{ }}.
     *
     * Flujo:
     * 1. docxtemplater-service (Node.js) reemplaza marcadores y adjunta firma
     * 2. Gotenberg (Docker) convierte DOCX -> PDF sin necesidad de LibreOffice local
     */
    public byte[] generarPdfDocumento(com.sistema.tramites.backend.tramite.Tramite tramite, boolean aprobado, String observacion) throws Exception {
        String templateName = obtenerNombrePlantilla(tramite, aprobado);
        byte[] docxProcesado = docxtemplaterService.processTemplate(templateName, tramite, aprobado, observacion);
        return convertirDocxAGotenberg(docxProcesado);
    }

    public String getMotorPdfConfigurado() {
        return gotenbergEnabled ? "Gotenberg" : "DOCX4J (interno)";
    }

    public String obtenerNombrePlantilla(com.sistema.tramites.backend.tramite.Tramite tramite, boolean aprobado) {
        if (!resolverDecisionAprobada(tramite, aprobado)) {
            return "RESPUESTA NEGATIVA.docx";
        }

        String tipoCertificado = tramite.getTipo_certificado() != null
                ? tramite.getTipo_certificado().trim().toUpperCase()
                : "";

        switch (tipoCertificado) {
            case "SISBEN":
            case "CERTIFICADO_SISBEN":
                return "CARTA RESIDENCIA SISBEN.docx";
            case "ELECTORAL":
            case "CERTIFICADO_ELECTORAL":
            case "REGISTRADURIA NACIONAL":
                return "CARTA RESIDENCIA REGISTRADURIA NACIONAL.docx";
            case "JAC":
            case "JUNTA DE ACCION":
            case "CERTIFICADO_RESIDENCIA":
                return "CARTA RESIDENCIA JUNTA DE ACCION.docx";
            default:
                return "CARTA RESIDENCIA JUNTA DE ACCION.docx";
        }
    }

    public byte[] convertirDocxAGotenberg(byte[] docxBytes) throws Exception {
        // NOTA IMPORTANTE: docx4j daña el formato de las plantillas (reformatea según FOP).
        // Gotenberg usa LibreOffice y preserva exactamente el diseño original.
        // Por eso Gotenberg es obligatorio y no hay fallback a docx4j interno.
        if (!gotenbergEnabled) {
            throw new RuntimeException(
                "Gotenberg está deshabilitado. La conversión DOCX→PDF requiere Gotenberg activo " +
                "(app.pdf.gotenberg.enabled=true). " +
                "Sin Gotenberg, los PDFs se dañan. Revisa la configuración o levanta Docker Compose."
            );
        }

        String boundary = "----tramite-" + java.util.UUID.randomUUID();
        String crlf = "\r\n";
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();

        bos.write(("--" + boundary + crlf).getBytes());
        bos.write(("Content-Disposition: form-data; name=\"files\"; filename=\"documento.docx\"" + crlf).getBytes());
        bos.write(("Content-Type: application/vnd.openxmlformats-officedocument.wordprocessingml.document" + crlf + crlf).getBytes());
        bos.write(docxBytes);
        bos.write(crlf.getBytes());
        bos.write(("--" + boundary + "--" + crlf).getBytes());

        byte[] multipartBody = bos.toByteArray();

        java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(15))
                .build();

        int attempts = Math.max(1, gotenbergMaxAttempts);
        RuntimeException lastError = null;
        for (int i = 1; i <= attempts; i++) {
            try {
                java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                        .uri(java.net.URI.create(gotenbergUrl))
                        .timeout(java.time.Duration.ofSeconds(45))
                        .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                        .header("Accept", "application/pdf")
                        .POST(java.net.http.HttpRequest.BodyPublishers.ofByteArray(multipartBody))
                        .build();

                java.net.http.HttpResponse<byte[]> response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofByteArray());
                if (response.statusCode() == 200) {
                    return response.body();
                }

                String errorBody = new String(response.body());
                lastError = new RuntimeException("Gotenberg error: " + response.statusCode() + " - " + errorBody);
                if (!esErrorReintentable(response.statusCode()) || i == attempts) {
                    throw lastError;
                }
            } catch (java.io.IOException ex) {
                lastError = new RuntimeException("Error de conexión/timeout con Gotenberg: " + ex.getMessage(), ex);
                if (i == attempts) {
                    throw lastError;
                }
            }

            try {
                Thread.sleep(Math.max(0L, gotenbergRetryDelayMs));
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Reintento de Gotenberg interrumpido", interruptedException);
            }
        }

        throw (lastError != null)
                ? lastError
                : new RuntimeException("Error desconocido al convertir con Gotenberg");
    }

    private boolean resolverDecisionAprobada(com.sistema.tramites.backend.tramite.Tramite tramite, boolean aprobadoSolicitado) {
        if (!aprobadoSolicitado) return false;
        if (tramite == null) return true;
        if (tramite.getVerificacionAprobada() != null) return tramite.getVerificacionAprobada();
        return tramite.getEstado() != com.sistema.tramites.backend.tramite.EstadoTramite.RECHAZADO;
    }

    private boolean esErrorReintentable(int statusCode) {
        return statusCode == 502 || statusCode == 503 || statusCode == 504;
    }

    public org.springframework.core.io.ResourceLoader getResourceLoader() {
        return new org.springframework.core.io.DefaultResourceLoader();
    }

    /**
     * Genera texto plano a partir de la plantilla y datos del trámite.
     */
    public String generarTextoDocumento(com.sistema.tramites.backend.tramite.Tramite tramite, boolean aprobado, String observacion) throws Exception {
        String plantilla = obtenerNombrePlantilla(tramite, aprobado);
        return "Documento generado: " + plantilla + " para trámite " + tramite.getNumeroRadicado();
    }

    /**
     * Genera una vista previa HTML del documento.
     */
    public String generarHtmlPreview(com.sistema.tramites.backend.tramite.Tramite tramite, boolean aprobado, String observacion) throws Exception {
        String plantilla = obtenerNombrePlantilla(tramite, aprobado);
        return "<div><b>Vista previa:</b> " + plantilla + " para trámite " + tramite.getNumeroRadicado() + "</div>";
    }

    /**
     * Genera PDF y lo adjunta al trámite (flujo especial).
     */
    public void generarYAdjuntarPdf(com.sistema.tramites.backend.tramite.Tramite tramite, boolean aprobado, String observacion) throws Exception {
        byte[] pdf = generarPdfDocumento(tramite, aprobado, observacion);
        tramite.setContenidoPdfGenerado(pdf);
        tramite.setNombrePdfGenerado("certificado.pdf");
        tramite.setMotorPdfGenerado(getMotorPdfConfigurado());
        tramite.setHashDocumentoGenerado(com.sistema.tramites.backend.util.HashUtils.sha256Hex(pdf));
    }
}
