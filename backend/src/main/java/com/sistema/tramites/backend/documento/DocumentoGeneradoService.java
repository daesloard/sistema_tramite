package com.sistema.tramites.backend.documento;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
package com.sistema.tramites.backend.documento;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class DocumentoGeneradoService {

    @Autowired
    private DocxtemplaterService docxtemplaterService;

    @Value("${app.pdf.gotenberg.url:http://localhost:3000/forms/libreoffice/convert}")
    private String gotenbergUrl;

    @Value("${app.pdf.gotenberg.enabled:true}")
    private boolean gotenbergEnabled;

    /**
     * Genera PDF a partir de plantilla DOCX con marcadores {{ }}.
     *
     * Flujo para producción (con Docker):
     * 1. docxtemplater-service (Node.js) reemplaza marcadores {{ }} SIN modificar formato
     * 2. Gotenberg convierte DOCX → PDF preservando diseño exacto
     */
    public byte[] generarPdfDocumento(com.sistema.tramites.backend.tramite.Tramite tramite, boolean aprobado, String observacion) throws Exception {
        String templateName = obtenerNombrePlantilla(tramite, aprobado);

        // Paso 1: Procesar plantilla con docxtemplater (Node.js)
        byte[] docxProcesado;
        try {
            docxProcesado = docxtemplaterService.processTemplate(templateName, tramite, aprobado, observacion);
            System.out.println("[DOCXTEMPLATER] Plantilla procesada: " + templateName);
        } catch (Exception e) {
            throw new RuntimeException("Error al procesar plantilla con docxtemplater: " + e.getMessage(), e);
        }

        // Guardar DOCX procesado para depuración
        try {
            java.nio.file.Files.write(java.nio.file.Paths.get("docx_generado_debug.docx"), docxProcesado);
            System.out.println("[DOCXTEMPLATER] DOCX procesado guardado como docx_generado_debug.docx");
        } catch (Exception ex) {
            System.out.println("[DOCXTEMPLATER] No se pudo guardar el DOCX procesado: " + ex.getMessage());
        }

        // Paso 2: Convertir a PDF con Gotenberg
        try {
            return convertirDocxAGotenberg(docxProcesado);
        } catch (Exception e) {
            throw new RuntimeException("Error al convertir DOCX a PDF con Gotenberg: " + e.getMessage(), e);
        }
    }

    public String obtenerNombrePlantilla(com.sistema.tramites.backend.tramite.Tramite tramite, boolean aprobado) {
        // Si no fue aprobado, usar plantilla NEGATIVA
        if (!aprobado || tramite.getVerificacionAprobada() != null && !tramite.getVerificacionAprobada()) {
            return "RESPUESTA NEGATIVA.docx";
        }

        // Usar tipo_certificado para seleccionar la plantilla correcta
        String tipoCertificado = tramite.getTipo_certificado() != null ? tramite.getTipo_certificado().trim().toUpperCase() : "";
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
                // Por defecto usar JUNTA DE ACCION
                System.out.println("[PLANTILLA] Tipo de certificado no reconocido: '" + tipoCertificado + "', usando JUNTA DE ACCION por defecto");
                return "CARTA RESIDENCIA JUNTA DE ACCION.docx";
        }
    }

    public byte[] convertirDocxAGotenberg(byte[] docxBytes) throws Exception {
        // Adaptar a multipart/form-data
        String boundary = "----tramite-" + java.util.UUID.randomUUID();
        String CRLF = "\r\n";
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        bos.write(("--" + boundary + CRLF).getBytes());
        bos.write(("Content-Disposition: form-data; name=\"files\"; filename=\"documento.docx\"" + CRLF).getBytes());
        bos.write(("Content-Type: application/vnd.openxmlformats-officedocument.wordprocessingml.document" + CRLF + CRLF).getBytes());
        bos.write(docxBytes);
        bos.write(CRLF.getBytes());
        bos.write(("--" + boundary + "--" + CRLF).getBytes());
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
            // Lógica básica: solo retorna un resumen textual
            String plantilla = obtenerNombrePlantilla(tramite, aprobado);
            return "Documento generado: " + plantilla + " para trámite " + tramite.getNumeroRadicado();
        }

        /**
         * Genera una vista previa HTML del documento.
         */
        public String generarHtmlPreview(com.sistema.tramites.backend.tramite.Tramite tramite, boolean aprobado, String observacion) throws Exception {
            // Lógica básica: retorna HTML simple
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
            return "Documento generado: " + plantilla + " para trámite " + tramite.getNumeroRadicado();
        }

        /**
         * Genera una vista previa HTML del documento.
         */
        public String generarHtmlPreview(com.sistema.tramites.backend.tramite.Tramite tramite, boolean aprobado, String observacion) throws Exception {
            // Lógica básica: retorna HTML simple
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

