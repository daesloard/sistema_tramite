package com.sistema.tramites.backend.documento;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class DocumentoGeneradoService {

    @Autowired
    private DocxtemplaterService docxtemplaterService;

    @Value("${app.pdf.gotenberg.url:}")
    private String gotenbergUrl;

    @Value("${app.pdf.gotenberg.enabled:true}")
    private boolean gotenbergEnabled;

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

    public String obtenerNombrePlantilla(com.sistema.tramites.backend.tramite.Tramite tramite, boolean aprobado) {
        if (!aprobado || (tramite.getVerificacionAprobada() != null && !tramite.getVerificacionAprobada())) {
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
        if (!gotenbergEnabled) {
            throw new RuntimeException("La conversión con Gotenberg está deshabilitada (app.pdf.gotenberg.enabled=false)");
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

        java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
        java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create(gotenbergUrl))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .header("Accept", "application/pdf")
                .POST(java.net.http.HttpRequest.BodyPublishers.ofByteArray(multipartBody))
                .build();

        java.net.http.HttpResponse<byte[]> response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) {
            throw new RuntimeException("Gotenberg error: " + response.statusCode() + " - " + new String(response.body()));
        }
        return response.body();
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
        tramite.setMotorPdfGenerado("Gotenberg");
        tramite.setHashDocumentoGenerado(com.sistema.tramites.backend.util.HashUtils.sha256Hex(pdf));
    }
}
