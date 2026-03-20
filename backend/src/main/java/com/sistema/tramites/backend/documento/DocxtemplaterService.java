package com.sistema.tramites.backend.documento;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sistema.tramites.backend.tramite.Tramite;
import com.sistema.tramites.backend.usuario.Usuario;
import com.sistema.tramites.backend.util.NumeroALetrasUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
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

    @Value("${app.docxtemplater.url:http://localhost:3001/render-docx}")
    private String docxtemplaterUrl;

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
        
        byte[] plantillaBytes = inputStream.readAllBytes();
        System.out.println("[DOCXTEMPLATER] Plantilla cargada: " + resourcePath + " (" + plantillaBytes.length + " bytes)");

        // Preparar datos para marcadores
        Map<String, String> datos = prepararDatos(tramite, aprobado, observacion);
        
        // Serializar datos a JSON
        ObjectMapper mapper = new ObjectMapper();
        String jsonData = mapper.writeValueAsString(datos);
        System.out.println("[DOCXTEMPLATER] JSON enviado: " + jsonData);

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

        // Enviar petición HTTP a docxtemplater-service
        var client = java.net.http.HttpClient.newHttpClient();
        var request = java.net.http.HttpRequest.newBuilder()
            .uri(java.net.URI.create(docxtemplaterUrl))
            .header("Content-Type", "multipart/form-data; boundary=" + boundary)
            .header("Accept", "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
            .POST(java.net.http.HttpRequest.BodyPublishers.ofByteArray(multipartBody))
            .build();
        
        var response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) {
            throw new RuntimeException("docxtemplater-service error: " + response.statusCode() + " - " + new String(response.body()));
        }
        
        byte[] docxProcesado = response.body();
        
        // Validar que el DOCX procesado no esté vacío
        if (docxProcesado == null || docxProcesado.length < 1000) {
            throw new RuntimeException("docxtemplater-service devolvió un archivo vacío o corrupto.");
        }
        
        return docxProcesado;
    }

    /**
     * Prepara el mapa de datos para reemplazar marcadores.
     * Incluye variantes para compatibilidad con diferentes plantillas.
     */
    private Map<String, String> prepararDatos(Tramite tramite, boolean aprobado, String observacion) throws Exception {
        Map<String, String> datos = new HashMap<>();
        LocalDateTime firmaDate = tramite.getFechaFirmaAlcalde();
        LocalDate firmaLocalDate = firmaDate != null ? firmaDate.toLocalDate() : LocalDate.now();

        // Datos básicos para todas las plantillas (todos en MAYÚSCULAS)
        datos.put("consecutivo", safeValue(tramite.getConsecutivoVerificador()).toUpperCase());
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
        String firmaBase64 = cargarFirmaBase64();
        datos.put("firma.jpeg", firmaBase64);

        datos.put("alcalde", safeNombreCompleto(tramite.getUsuarioAlcalde()).toUpperCase());
        datos.put("verificador", safeNombreCompleto(tramite.getUsuarioVerificador()).toUpperCase());
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
