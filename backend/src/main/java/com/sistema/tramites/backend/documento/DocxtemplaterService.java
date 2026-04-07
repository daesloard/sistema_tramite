package com.sistema.tramites.backend.documento;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sistema.tramites.backend.tramite.Tramite;
import com.sistema.tramites.backend.usuario.Usuario;
import com.sistema.tramites.backend.util.NumeroALetrasUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.Map;

/**
 * Servicio que se comunica con docxtemplater-service.js (Node.js)
 * para procesar plantillas DOCX con marcadores {{ }}.
 * 
 * Ventaja: NO modifica el formato de la plantilla (fuentes, márgenes, espaciados).
 */
@Service
public class DocxtemplaterService {

    @Autowired
    private DocxTemplateProcessor docxTemplateProcessor;

    @Value("${app.docxtemplater.enabled:false}")
    private boolean docxtemplaterEnabled;

    @Value("${app.docxtemplater.url:}")
    private String docxtemplaterUrl;

    @Value("${app.docxtemplater.max-attempts:3}")
    private int docxtemplaterMaxAttempts;

    @Value("${app.docxtemplater.retry-delay-ms:700}")
    private long docxtemplaterRetryDelayMs;

    @Value("${app.docxtemplater.fallback-enabled:true}")
    private boolean docxtemplaterFallbackEnabled;

    /**
     * Procesa plantilla DOCX reemplazando marcadores {{nombre}}.
     *
     * @param templateName Nombre del archivo en classpath:templates/
     * @param tramite Datos del trámite
     * @param aprobado Estado de aprobación
     * @param observacion Observaciones
     * @return DOCX procesado con marcadores reemplazados
     * @throws Exception Si falla el procesamiento
     */
    public byte[] processTemplate(String templateName, Tramite tramite, boolean aprobado, String observacion) throws Exception {
        byte[] firmaBytes = cargarFirmaBytes();
        Map<String, String> datos = prepararDatos(tramite, aprobado, observacion, docxtemplaterEnabled);
        if (!docxtemplaterEnabled) {
            System.out.println("[DOCXTEMPLATER] Deshabilitado, usando procesador interno.");
            return docxTemplateProcessor.processTemplate(templateName, datos, firmaBytes);
        }

        try {
            return processTemplateWithDocxtemplater(templateName, datos, firmaBytes);
        } catch (Exception ex) {
            if (docxtemplaterFallbackEnabled) {
                System.err.println("[DOCXTEMPLATER] Fallo del servicio externo, activando fallback interno: " + ex.getMessage());
                return docxTemplateProcessor.processTemplate(templateName, datos, firmaBytes);
            }
            throw ex;
        }
    }

    private byte[] processTemplateWithDocxtemplater(String templateName, Map<String, String> datos, byte[] firmaBytes) throws Exception {

        // Cargar plantilla DOCX desde classpath
        String resourcePath = "templates/" + templateName;
        java.io.InputStream inputStream = getClass().getClassLoader().getResourceAsStream(resourcePath);
        
        if (inputStream == null) {
            // Intentar con ruta alternativa
            String altPath = "classpath:templates/" + templateName;
            inputStream = getClass().getClassLoader().getResourceAsStream(altPath);
        }
        
        if (inputStream == null) {
            throw new RuntimeException("Plantilla no encontrada: " + resourcePath);
        }

        byte[] plantillaBytes;
        try (java.io.InputStream is = inputStream) {
            plantillaBytes = is.readAllBytes();
        }
        System.out.println("[DOCXTEMPLATER] Plantilla cargada: " + resourcePath + " (" + plantillaBytes.length + " bytes)");

        // Preparar datos para marcadores
        // Serializar datos a JSON
        ObjectMapper mapper = new ObjectMapper();
        String jsonData = mapper.writeValueAsString(datos);
        System.out.println("[DOCXTEMPLATER] JSON enviado: " + mapper.writeValueAsString(resumirDatosLog(datos)));

        // Construir multipart/form-data
        String boundary = "----docxtemplater-" + java.util.UUID.randomUUID();
        String CRLF = "\r\n";
        var bos = new java.io.ByteArrayOutputStream();
        
        // Parte 1: Archivo DOCX
        bos.write(("--" + boundary + CRLF).getBytes());
        bos.write(("Content-Disposition: form-data; name=\"files\"; filename=\"" + templateName + "\"" + CRLF).getBytes());
        bos.write(("Content-Type: application/vnd.openxmlformats-officedocument.wordprocessingml.document" + CRLF + CRLF).getBytes());
        bos.write(plantillaBytes);
        bos.write(CRLF.getBytes());
        
        // Parte 2: JSON con datos
        bos.write(("--" + boundary + CRLF).getBytes());
        bos.write(("Content-Disposition: form-data; name=\"data\"" + CRLF).getBytes());
        bos.write(("Content-Type: application/json" + CRLF + CRLF).getBytes());
        bos.write(jsonData.getBytes());
        bos.write(CRLF.getBytes());
        
        // Cerrar multipart
        bos.write(("--" + boundary + "--" + CRLF).getBytes());
        byte[] multipartBody = bos.toByteArray();

        System.out.println("[DOCXTEMPLATER] Llamando a: " + docxtemplaterUrl);

        // Enviar petición HTTP a docxtemplater-service con reintentos para evitar caídas transitorias.
        var client = java.net.http.HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(15))
                .build();

        byte[] docxProcesado = null;
        int attempts = Math.max(1, docxtemplaterMaxAttempts);
        RuntimeException lastError = null;
        for (int i = 1; i <= attempts; i++) {
            try {
                var request = java.net.http.HttpRequest.newBuilder()
                        .uri(java.net.URI.create(docxtemplaterUrl))
                        .timeout(java.time.Duration.ofSeconds(35))
                        .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                        .header("Accept", "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                        .POST(java.net.http.HttpRequest.BodyPublishers.ofByteArray(multipartBody))
                        .build();

                var response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofByteArray());
                if (response.statusCode() == 200) {
                    docxProcesado = response.body();
                    break;
                }

                String errorBody = new String(response.body());
                lastError = new RuntimeException("docxtemplater-service error: " + response.statusCode() + " - " + errorBody);
                if (!esErrorReintentable(response.statusCode()) || i == attempts) {
                    throw lastError;
                }
            } catch (java.io.IOException ex) {
                lastError = new RuntimeException("Error de conexión/timeout con docxtemplater-service: " + ex.getMessage(), ex);
                System.err.println("[DOCXTEMPLATER] Intento " + i + "/" + attempts + " fallido: " + ex.getMessage());
                if (i == attempts) {
                    throw lastError;
                }
            }

            try {
                Thread.sleep(Math.max(0L, docxtemplaterRetryDelayMs));
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Reintento de docxtemplater interrumpido", interruptedException);
            }
        }

        if (docxProcesado == null) {
            throw (lastError != null) ? lastError : new RuntimeException("docxtemplater-service no devolvió respuesta válida");
        }
        
        // Validar que el DOCX procesado no esté vacío
        if (docxProcesado == null || docxProcesado.length < 1000) {
            throw new RuntimeException("docxtemplater-service devolvió un archivo vacío o corrupto.");
        }
        
        // Aplicar un segundo pase en POI para cubrir marcadores remanentes en headers/footers
        // y garantizar inserción de firma en imagen.
        return docxTemplateProcessor.processDocument(docxProcesado, datos, firmaBytes);
    }

    private boolean esErrorReintentable(int statusCode) {
        return statusCode == 502 || statusCode == 503 || statusCode == 504;
    }

    /**
     * Prepara el mapa de datos para reemplazar marcadores.
     * Incluye variantes para compatibilidad con diferentes plantillas.
     */
    private Map<String, String> prepararDatos(Tramite tramite, boolean aprobado, String observacion, boolean preservarMarcadoresFirma) throws Exception {
        Map<String, String> datos = new HashMap<>();
        LocalDateTime firmaDate = tramite.getFechaFirmaAlcalde();
        LocalDate firmaLocalDate = firmaDate != null ? firmaDate.toLocalDate() : LocalDate.now();

        // Datos básicos para todas las plantillas (todos en MAYÚSCULAS)
        String consecutivo = safeValue(tramite.getConsecutivoVerificador());
        datos.put("consecutivo", consecutivo.toUpperCase());
        datos.put("nombreSolicitante", safeValue(tramite.getNombreSolicitante()).toUpperCase());
        datos.put("nombre_colicitante", safeValue(tramite.getNombreSolicitante()).toUpperCase()); // Para RESPUESTA NEGATIVA
        datos.put("numeroDocumento", safeValue(tramite.getNumeroDocumento()).toUpperCase());
        datos.put("lugarExpedicionDocumento", safeValue(tramite.getLugarExpedicionDocumento()).toUpperCase());
        datos.put("direccionResidencia", safeValue(tramite.getDireccionResidencia()).toUpperCase());
        datos.put("dias", String.valueOf(firmaLocalDate.getDayOfMonth()));
        datos.put("diasLetras", NumeroALetrasUtil.numeroALetras(firmaLocalDate.getDayOfMonth()).toUpperCase());
        datos.put("mesLetras", NumeroALetrasUtil.mesALetras(firmaLocalDate).toUpperCase());
        datos.put("año", String.valueOf(firmaLocalDate.getYear()));
        datos.put("añoLetra", NumeroALetrasUtil.anioALetras(firmaLocalDate.getYear()).toUpperCase());

        // Cargar imagen de firma y convertir a base64
        if (preservarMarcadoresFirma) {
            datos.put("firma.jpeg", "{{firma.jpeg}}");
            datos.put("firma.jpg", "{{firma.jpg}}");
            datos.put("firma", "{{firma}}");
        } else {
            String firmaBase64 = cargarFirmaBase64();
            datos.put("firma.jpeg", firmaBase64);
            datos.put("firma.jpg", firmaBase64);
            datos.put("firma", firmaBase64);
        }

        String alcalde = safeNombreCompleto(tramite.getUsuarioAlcalde());
        String verificador = safeNombreCompleto(tramite.getUsuarioVerificador());
        if (alcalde.isBlank()) {
            alcalde = "ALCALDE MUNICIPAL";
        }
        if (verificador.isBlank()) {
            verificador = "VERIFICADOR";
        }
        datos.put("alcalde", alcalde.toUpperCase());
        datos.put("verificador", verificador.toUpperCase());
        datos.put("numeroRadico", safeValue(tramite.getNumeroRadicado()).toUpperCase());
        datos.put("numeroRadicado", safeValue(tramite.getNumeroRadicado()).toUpperCase()); // Variante sin 'o' final
        datos.put("fechaFirma", firmaDate != null ? firmaDate.toString() : "");
        datos.put("observacion", safeValue(tramite.getObservaciones()).toUpperCase());
        datos.put("tipoDocumento", safeValue(tramite.getTipoDocumento()).toUpperCase()); // Para RESPUESTA NEGATIVA

        return datos;
    }

    /**
     * Carga la imagen de firma desde resources y la retorna en base64.
     */
    private String cargarFirmaBase64() throws Exception {
        try {
            // Buscar en templates/firma.jpeg dentro de resources
            java.io.InputStream inputStream = getClass().getClassLoader().getResourceAsStream("templates/firma.jpeg");
            if (inputStream == null) {
                // Intentar ruta alternativa
                inputStream = getClass().getResourceAsStream("/templates/firma.jpeg");
            }
            if (inputStream == null) {
                System.out.println("[FIRMA] No se encontró firma.jpeg en classpath");
                // Listar recursos disponibles para debug
                var resources = getClass().getClassLoader().getResources("templates");
                while (resources.hasMoreElements()) {
                    System.out.println("[FIRMA] Resource encontrado: " + resources.nextElement());
                }
                return "";
            }
            byte[] firmaBytes = inputStream.readAllBytes();
            String base64 = java.util.Base64.getEncoder().encodeToString(firmaBytes);
            System.out.println("[FIRMA] Imagen cargada: " + firmaBytes.length + " bytes");
            return base64;
        } catch (Exception e) {
            System.out.println("[FIRMA] Error al cargar firma: " + e.getMessage());
            e.printStackTrace();
            return "";
        }
    }

    private byte[] cargarFirmaBytes() {
        try {
            java.io.InputStream inputStream = getClass().getClassLoader().getResourceAsStream("templates/firma.jpeg");
            if (inputStream == null) {
                inputStream = getClass().getResourceAsStream("/templates/firma.jpeg");
            }
            if (inputStream == null) {
                return new byte[0];
            }
            try (java.io.InputStream is = inputStream) {
                return is.readAllBytes();
            }
        } catch (Exception ex) {
            return new byte[0];
        }
    }

    private Map<String, Object> resumirDatosLog(Map<String, String> datos) {
        Map<String, Object> resumen = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : datos.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (key == null) {
                continue;
            }

            if (key.toLowerCase().startsWith("firma")) {
                if (value == null || value.isBlank()) {
                    resumen.put(key, "");
                } else if (value.startsWith("{{") && value.endsWith("}}")) {
                    resumen.put(key, value);
                } else {
                    resumen.put(key, "<base64:" + value.length() + " chars>");
                }
                continue;
            }

            if (value != null && value.length() > 180) {
                resumen.put(key, value.substring(0, 180) + "...");
            } else {
                resumen.put(key, value);
            }
        }
        return resumen;
    }

    private String safeValue(String value) {
        if (value == null) return "";
        String v = value.trim();
        return v.equalsIgnoreCase("null") ? "" : v;
    }

    private String safeNombreCompleto(Usuario usuario) {
        if (usuario == null) return "";
        return safeValue(usuario.getNombreCompleto());
    }
}
