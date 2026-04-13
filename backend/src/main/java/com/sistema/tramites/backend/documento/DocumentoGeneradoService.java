package com.sistema.tramites.backend.documento;

import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.PdfStamper;
import com.itextpdf.text.pdf.PdfWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

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

    @Value("${app.pdf.gotenberg.max-delay-ms:10000}")
    private long gotenbergMaxDelayMs;

    @Value("${app.pdf.gotenberg.max-concurrent:1}")
    private int gotenbergMaxConcurrent;

    @Value("${app.pdf.gotenberg.queue-timeout-seconds:30}")
    private long gotenbergQueueTimeoutSeconds;

    private Semaphore gotenbergSemaphore;

    @Value("${app.pdf.gotenberg.fallback-enabled:false}")
    private boolean gotenbergFallbackEnabled;

    @Value("${app.pdf.use-docx4j:false}")
    private boolean docx4jEnabled;

    @PostConstruct
    public void inicializarControlConcurrenciaGotenberg() {
        int maxConcurrent = Math.max(1, gotenbergMaxConcurrent);
        this.gotenbergSemaphore = new Semaphore(maxConcurrent, true);
    }

    /**
     * Genera PDF a partir de plantilla DOCX con marcadores {{ }}.
     *
     * Flujo:
     * 1. docxtemplater-service (Node.js) reemplaza marcadores y adjunta firma
     * 2. Gotenberg (Docker) convierte DOCX -> PDF sin necesidad de LibreOffice local
     */
    public byte[] generarPdfDocumento(com.sistema.tramites.backend.tramite.Tramite tramite, boolean aprobado, String observacion) throws Exception {
        String templateName = obtenerNombrePlantilla(tramite, aprobado);
        System.out.println("[PDF] Iniciando generacion. Plantilla=" + templateName
                + " | gotenbergEnabled=" + gotenbergEnabled
                + " | docx4jEnabled=" + docx4jEnabled
                + " | gotenbergFallbackEnabled=" + gotenbergFallbackEnabled
                + " | gotenbergUrl=" + gotenbergUrl);
        try {
            byte[] docxProcesado = docxtemplaterService.processTemplate(templateName, tramite, aprobado, observacion);
            System.out.println("[PDF] DOCX procesado OK (" + docxProcesado.length + " bytes). Convirtiendo a PDF...");
            byte[] pdf = convertirDocxAPdf(docxProcesado);
            byte[] pdfProtegido = protegerPdfContraEdicion(pdf);
            System.out.println("[PDF] PDF generado y protegido OK (" + pdfProtegido.length + " bytes).");
            return pdfProtegido;
        } catch (Exception ex) {
            System.err.println("[PDF] ERROR al generar PDF: " + ex.getMessage());
            throw ex;
        }
    }

    private byte[] protegerPdfContraEdicion(byte[] pdfBytes) throws Exception {
        if (pdfBytes == null || pdfBytes.length == 0) {
            throw new IllegalArgumentException("No se puede proteger un PDF vacío");
        }

        try (java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
            PdfReader reader = new PdfReader(pdfBytes);
            String ownerPassword = "owner-" + UUID.randomUUID();
            PdfStamper stamper = new PdfStamper(reader, out);

            // Permite visualización/impresión, pero bloquea edición/copia/comentarios.
            stamper.setEncryption(
                    null,
                    ownerPassword.getBytes(StandardCharsets.UTF_8),
                    PdfWriter.ALLOW_PRINTING,
                    PdfWriter.ENCRYPTION_AES_256
            );

            stamper.close();
            reader.close();
            return out.toByteArray();
        }
    }

    public String getMotorPdfConfigurado() {
        if (gotenbergEnabled && gotenbergFallbackEnabled && docx4jEnabled) {
            return "Gotenberg (fallback DOCX4J)";
        }
        if (gotenbergEnabled) {
            return "Gotenberg";
        }
        if (docx4jEnabled) {
            return "DOCX4J (interno)";
        }
        return "No configurado";
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

    public byte[] convertirDocxAPdf(byte[] docxBytes) throws Exception {
        if (docxBytes == null || docxBytes.length == 0) {
            throw new IllegalArgumentException("No se puede convertir un DOCX vacío");
        }

        Exception gotenbergException = null;

        if (gotenbergEnabled) {
            try {
                return convertirSoloConGotenberg(docxBytes);
            } catch (Exception ex) {
                gotenbergException = ex;
                if (!(gotenbergFallbackEnabled && docx4jEnabled)) {
                    throw ex;
                }
                System.err.println("[PDF] Fallback activado: Gotenberg falló y se intentará DOCX4J. Causa=" + ex.getMessage());
            }
        }

        if (docx4jEnabled) {
            return docx4jPdfConverterService.convert(docxBytes);
        }

        StringBuilder mensaje = new StringBuilder(
                "No hay motor PDF habilitado. Configura app.pdf.gotenberg.enabled=true o app.pdf.use-docx4j=true"
        );
        if (gotenbergException != null) {
            mensaje.append(". Error previo de Gotenberg: ").append(gotenbergException.getMessage());
        }
        throw new RuntimeException(mensaje.toString());
    }

    public byte[] convertirDocxAGotenberg(byte[] docxBytes) throws Exception {
        return convertirDocxAPdf(docxBytes);
    }

    private byte[] convertirSoloConGotenberg(byte[] docxBytes) throws Exception {
        if (!gotenbergEnabled) {
            throw new RuntimeException("Gotenberg está deshabilitado por configuración");
        }
        if (gotenbergUrl == null || gotenbergUrl.isBlank()) {
            throw new RuntimeException("app.pdf.gotenberg.url está vacío o no configurado");
        }

        boolean permitAcquired = false;
        try {
            permitAcquired = gotenbergSemaphore.tryAcquire(
                    Math.max(1L, gotenbergQueueTimeoutSeconds),
                    TimeUnit.SECONDS
            );
            if (!permitAcquired) {
                throw new RuntimeException("Cola de conversión PDF saturada. Intente nuevamente en unos segundos");
            }

            return convertirSoloConGotenbergInterno(docxBytes);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrumpido esperando turno de conversión PDF", interruptedException);
        } finally {
            if (permitAcquired) {
                gotenbergSemaphore.release();
            }
        }
    }

    private byte[] convertirSoloConGotenbergInterno(byte[] docxBytes) throws Exception {

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

                int statusCode = response.statusCode();
                String errorBody = new String(response.body());
                lastError = new RuntimeException("Gotenberg error: " + statusCode + " - " + errorBody);
                // Si hay fallback disponible y es un 429, no reintentamos: fallamos rápido para ceder a DOCX4J
                boolean tieneFallback = gotenbergFallbackEnabled && docx4jEnabled;
                if (!esErrorReintentable(statusCode) || i == attempts || (statusCode == 429 && tieneFallback)) {
                    throw lastError;
                }

                String retryAfterHeader = response.headers().firstValue("Retry-After").orElse(null);
                long waitMs = calcularEsperaReintentoMs(i, statusCode, retryAfterHeader);
                System.err.println("[PDF] Gotenberg intento " + i + "/" + attempts
                        + " falló con status=" + statusCode
                        + ". Reintentando en " + waitMs + " ms");
                dormirReintento(waitMs);
            } catch (java.io.IOException ex) {
                lastError = new RuntimeException("Error de conexión/timeout con Gotenberg: " + ex.getMessage(), ex);
                if (i == attempts) {
                    throw lastError;
                }

                long waitMs = calcularEsperaReintentoMs(i, 0, null);
                System.err.println("[PDF] Gotenberg intento " + i + "/" + attempts
                        + " falló por IO/timeout. Reintentando en " + waitMs + " ms");
                dormirReintento(waitMs);
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
        return statusCode == 429 || statusCode == 502 || statusCode == 503 || statusCode == 504;
    }

    private long calcularEsperaReintentoMs(int intentoActual, int statusCode, String retryAfterHeader) {
        long retryAfterMs = parseRetryAfterMs(retryAfterHeader);
        if (statusCode == 429 && retryAfterMs > 0) {
            return retryAfterMs;
        }

        long baseDelay = Math.max(0L, gotenbergRetryDelayMs);
        long maxDelay = Math.max(baseDelay, gotenbergMaxDelayMs);
        long factorExponencial = 1L << Math.max(0, intentoActual - 1);
        long exponencial = baseDelay * factorExponencial;
        long capped = Math.min(exponencial, maxDelay);
        long jitter = (long) (Math.random() * Math.max(250L, baseDelay / 2));
        return Math.min(maxDelay, capped + jitter);
    }

    private long parseRetryAfterMs(String retryAfterHeader) {
        if (retryAfterHeader == null || retryAfterHeader.isBlank()) {
            return -1L;
        }
        try {
            long seconds = Long.parseLong(retryAfterHeader.trim());
            return Math.max(0L, seconds * 1000L);
        } catch (NumberFormatException ex) {
            return -1L;
        }
    }

    private void dormirReintento(long waitMs) {
        try {
            Thread.sleep(Math.max(0L, waitMs));
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Reintento de Gotenberg interrumpido", interruptedException);
        }
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
        String _docNum = tramite.getNumeroDocumento() != null ? tramite.getNumeroDocumento().replaceAll("[^a-zA-Z0-9]", "") : "sin_documento";
        tramite.setNombrePdfGenerado(aprobado ? "residencia_" + _docNum + ".pdf" : "residencia_negada_" + _docNum + ".pdf");
        tramite.setMotorPdfGenerado(getMotorPdfConfigurado());
        tramite.setHashDocumentoGenerado(com.sistema.tramites.backend.util.HashUtils.sha256Hex(pdf));
    }
}
