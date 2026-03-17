package com.sistema.tramites.backend.documento;

import jakarta.annotation.PostConstruct;
import com.sistema.tramites.backend.tramite.Tramite;
import com.sistema.tramites.backend.documento.DocxTemplateProcessor;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.SimpleClientHttpRequestFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.Certificate;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.docx4j.Docx4J;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import com.sistema.tramites.backend.util.NumeroALetrasUtil;
import java.time.LocalDate;
import com.sistema.tramites.backend.usuario.Usuario;

@Service
public class DocumentoGeneradoService {
    private static final Locale LOCALE_ES = new Locale("es", "CO");
    private static final Logger logger = LoggerFactory.getLogger(DocumentoGeneradoService.class);
    private final MeterRegistry meterRegistry;
    private final boolean incluirDetalleFirmaEnPdf;
    private final boolean firmaDigitalHabilitada;
    private final String certificadoP12Path;
    private final String certificadoP12Password;
    private final String certificadoP12Alias;
    private volatile CertBundle certBundleCache;

    @Value("${app.pdf.gotenberg.enabled:false}")
    private boolean useGotenberg;

    @Value("${app.pdf.gotenberg.url:}")
    private String gotenbergUrl;

    @Value("${app.pdf.gotenberg.timeout-seconds:25}")
    private int gotenbergTimeoutSeconds;

    private final RestTemplate restTemplate;

    static {
        if (Security.getProvider("BC") == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private final DocxTemplateProcessor docxProcessor;

    public DocumentoGeneradoService(DocxTemplateProcessor docxProcessor,
                                    RestTemplate restTemplate,
                                    MeterRegistry meterRegistry,
                                    @Value("${app.pdf.signature-annotation.enabled:false}") boolean incluirDetalleFirmaEnPdf,
                                    @Value("${app.pdf.digital-sign.enabled:true}") boolean firmaDigitalHabilitada,
                                    @Value("${app.pdf.digital-sign.p12-path:}") String certificadoP12Path,
                                    @Value("${app.pdf.digital-sign.p12-password:}") String certificadoP12Password,
                                    @Value("${app.pdf.digital-sign.p12-alias:}") String certificadoP12Alias) {
        this.docxProcessor = docxProcessor;
        this.restTemplate = restTemplate;
        this.meterRegistry = meterRegistry;
        this.incluirDetalleFirmaEnPdf = incluirDetalleFirmaEnPdf;
        this.firmaDigitalHabilitada = firmaDigitalHabilitada;
        this.certificadoP12Path = certificadoP12Path;
        this.certificadoP12Password = certificadoP12Password;
        this.certificadoP12Alias = certificadoP12Alias;
    }

    @PostConstruct
    public void init() {
        String[] engines = {"spire.doc", "poi+pdfbox"};
        String[] outcomes = {"success", "error"};
        String[] tipos = {"aprobado", "rechazado"};
        for (String engine : engines) {
            for (String tipo : tipos) {
                Counter.builder("tramites.pdf.generation.errors")
                        .tag("engine", engine)
                        .tag("tipo", tipo)
                        .register(meterRegistry);
                for (String outcome : outcomes) {
                    Timer.builder("tramites.pdf.generation.duration")
                            .tag("engine", engine)
                            .tag("outcome", outcome)
                            .tag("tipo", tipo)
                            .register(meterRegistry);
                }
            }
        }
    }

    public void generarYAdjuntarPdf(Tramite tramite, boolean aprobado, String observacion) {
        long inicioNanos = System.nanoTime();
        String tipo = aprobado ? "aprobado" : "rechazado";
        String engine = "poi-docx";
        String outcome = "success";
        try {
            byte[] pdfBytes = generarPdfFromTemplate(tramite, aprobado, observacion);
            byte[] pdfProtegido = protegerPdfContraEdicionYCopia(pdfBytes, tramite);
            byte[] pdfFirmado = firmarPdfDigitalmente(pdfProtegido, tramite);
            tramite.setContenidoPdfGenerado(pdfFirmado);
            tramite.setNombrePdfGenerado(generarNombrePdf(tramite, aprobado));
            tramite.setTipoContenidoPdfGenerado("application/pdf");
            tramite.setMotorPdfGenerado(engine);
        } catch (Exception ex) {
            outcome = "error";
            Counter.builder("tramites.pdf.generation.errors")
                    .description("Errores en la generacion de PDF")
                    .tag("engine", engine)
                    .tag("tipo", tipo)
                    .register(meterRegistry)
                    .increment();
            logger.error("Error generando PDF para trámite {}: {}", tramite.getId(), ex.getMessage(), ex);
            throw new RuntimeException("Error al generar PDF para trámite " + tramite.getId(), ex);
        } finally {
            long duracionNanos = System.nanoTime() - inicioNanos;
            Timer.builder("tramites.pdf.generation.duration")
                    .description("Duracion de generacion de PDF")
                    .tag("engine", engine)
                    .tag("outcome", outcome)
                    .tag("tipo", tipo)
                    .register(meterRegistry)
                    .record(duracionNanos, TimeUnit.NANOSECONDS);
        }
    }

    public byte[] generarPdfDocumento(Tramite tramite, boolean aprobado, String observacion) throws IOException {
        return generarPdfFromTemplate(tramite, aprobado, observacion);
    }

    private byte[] generarPdfFromTemplate(Tramite tramite, boolean aprobado, String observacion) throws IOException {
        String templateName;
        if (!aprobado) {
            templateName = "RESPUESTA NEGATIVA.docx";
        } else {
            String tipo = safeValue(tramite.getTipo_certificado());
            switch (tipo.toUpperCase()) {
                case "JUNTA DE ACCION": case "JAC": templateName = "CARTA RESIDENCIA JUNTA DE ACCION.docx"; break;
                case "REGISTRADURIA NACIONAL": case "REGISTRADURIA": case "ELECTORAL": templateName = "CARTA RESIDENCIA REGISTRADURIA NACIONAL.docx"; break;
                case "SISBEN": templateName = "CARTA RESIDENCIA SISBEN.docx"; break;
                default: templateName = "CARTA RESIDENCIA JUNTA DE ACCION.docx"; // fallback
            }
        }
        byte[] docxBytes = docxProcessor.processTemplate(templateName, tramite, aprobado, observacion);
        if (useGotenberg && !gotenbergUrl.isBlank()) {
            logger.info("Usando Gotenberg para convertir DOCX a PDF: {}", templateName);
            ByteArrayResource docxResource = new ByteArrayResource(docxBytes) {
                @Override
                public String getFilename() {
                    return "input.docx";
                }
            };
            MultiValueMap<String, Object> formData = new LinkedMultiValueMap<>();
            formData.add("files", docxResource);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(formData, headers);
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(gotenbergTimeoutSeconds * 1000);
            factory.setReadTimeout(gotenbergTimeoutSeconds * 1000);
            RestTemplate gotenbergClient = new RestTemplate(factory);
            ResponseEntity<byte[]> response = gotenbergClient.postForEntity(gotenbergUrl, requestEntity, byte[].class);
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                logger.info("PDF generado con Gotenberg desde template: {}", templateName);
                return response.getBody();
            } else {
                logger.error("Gotenberg falló: {}", response.getStatusCode());
                throw new RuntimeException("Gotenberg conversion failed");
            }
        } else {
            logger.info("Usando docx4j para convertir DOCX a PDF (Gotenberg deshabilitado): {}", templateName);
            WordprocessingMLPackage wordMLPackage = WordprocessingMLPackage.load(new ByteArrayInputStream(docxBytes));
            ByteArrayOutputStream pdfOut = new ByteArrayOutputStream();
            Docx4J.toPDF(wordMLPackage, pdfOut);
            pdfOut.close();
            logger.info("PDF generado con docx4j desde template: {}", templateName);
            return pdfOut.toByteArray();
        }
    }

    // Dummy fallback removed - Gotenberg forces template usage

    private static String safeValue(String value) {
        return value != null ? value.trim() : "";
    }

    private byte[] protegerPdfContraEdicionYCopia(byte[] pdfOriginal, Tramite tramite) {
        return pdfOriginal; // Placeholder
    }

    private byte[] firmarPdfDigitalmente(byte[] pdfOriginal, Tramite tramite) {
        return pdfOriginal; // Placeholder
    }

    private String generarNombrePdf(Tramite tramite, boolean aprobado) {
        String prefijo = aprobado ? "CERTIFICADO_RESIDENCIA_" : "RESPUESTA_NEGATIVA_";
        return prefijo + tramite.getNumeroRadicado() + ".pdf";
    }

    public String generarTextoDocumento(Tramite tramite, boolean aprobado, String observacion) {
        StringBuilder sb = new StringBuilder();
        sb.append(aprobado ? "CERTIFICADO DE RESIDENCIA" : "RESPUESTA NEGATIVA").append("\n");
        sb.append("Radicado: ").append(tramite.getNumeroRadicado()).append("\n");
        sb.append("Solicitante: ").append(tramite.getNombreSolicitante()).append("\n");
        sb.append("Documento: ").append(tramite.getNumeroDocumento()).append("\n");
        sb.append("Tipo: ").append(tramite.getTipo_certificado()).append("\n");
        if (observacion != null && !observacion.isBlank()) {
            sb.append("Observaciones: ").append(observacion).append("\n");
        }
        return sb.toString();
    }

    /**
     * HTML Preview para panel alcalde - Imita layout oficial DOCX exactamente
     */
    public String generarHtmlPreview(Tramite tramite, boolean aprobado, String observacion) {
        StringBuilder html = new StringBuilder();
        String titulo = aprobado ? "CERTIFICADO DE RESIDENCIA" : "RESPUESTA NEGATIVA";
        String tipoCert = safeValue(tramite.getTipo_certificado());
        LocalDateTime firmaDate = tramite.getFechaFirmaAlcalde();
        LocalDate fechaFirma = firmaDate != null ? firmaDate.toLocalDate() : LocalDate.now();
        
        String diasLetras = NumeroALetrasUtil.numeroALetras(fechaFirma.getDayOfMonth());
        String mesLetras = NumeroALetrasUtil.mesALetras(fechaFirma);
        String anoLetras = NumeroALetrasUtil.anioALetras(fechaFirma.getYear());
        
        String verificador = safeNombreCompleto(tramite.getUsuarioVerificador());
        String alcalde = safeNombreCompleto(tramite.getUsuarioAlcalde());
        
        String observacionHtml = aprobado ? "" : "<div class=\"negativa\"><strong>Solicitud rechazada por:</strong><br/>" + safeValue(observacion) + "</div>";
        
        html.append("<!DOCTYPE html><html><head><meta charset=\"utf-8\"><title>Preview " + titulo + "</title>");
        html.append("<style>@page { margin: 2cm; size: A4; }");
        html.append("body { font-family: 'Maven Pro', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; font-size: 12pt; line-height: 1.4; margin: 0; padding: 2cm; color: #000; background: white; }");
        html.append(".titulo-principal, .municipio, .nombre-firma { font-weight: 700; } /* Maven Pro Bold simulado */");
        html.append(".header { text-align: center; margin-bottom: 30px; } .escudo { max-width: 80px; height: auto; margin-bottom: 15px; }");
        html.append(".titulo-principal { font-size: 18pt; font-weight: bold; margin: 20px 0; text-transform: uppercase; letter-spacing: 1px; }");
        html.append(".municipio { font-weight: bold; font-size: 14pt; color: #d32f2f; }");
        html.append(".datos { width: 100%; margin: 30px 0; border-collapse: collapse; } .datos td { padding: 12px 8px; vertical-align: top; border-bottom: 1px solid #ccc; }");
        html.append(".label { font-weight: bold; text-align: right; width: 40%; color: #333; } .value { font-size: 11pt; padding-left: 10px; }");
        html.append(".firmas { display: flex; justify-content: space-between; margin-top: 60px; } .firma { text-align: center; width: 45%; }");
        html.append(".linea-firma { height: 1px; border-bottom: 1px solid #000; margin: 0 auto 10px; width: 200px; }");
        html.append(".nombre-firma { font-weight: bold; margin-top: 10px; font-size: 12pt; } .cargo { font-size: 10pt; color: #666; margin-top: 5px; }");
        html.append(".fecha { font-size: 11pt; margin-top: 40px; text-align: center; font-style: italic; font-weight: bold; }");
        html.append(".negativa { background: #f8d7da; border: 2px solid #dc3545; padding: 25px; text-align: center; margin: 20px 0; color: #721c24; border-radius: 5px; font-weight: bold; }");
        html.append("@media print { body { -webkit-print-color-adjust: exact; } }</style></head><body>");
        html.append("<div class=\"header\"><img src=\"/static/escudo.png\" alt=\"Escudo\" class=\"escudo\">");
        html.append("<div class=\"titulo-principal\">" + titulo + "</div>");
        html.append("<div class=\"municipio\">MUNICIPIO DE CABUYARO</div><div style=\"font-size: 11pt;\">Alcaldía Municipal - Meta</div></div>");
        html.append("<table class=\"datos\">");
        html.append("<tr><td class=\"label\">Radicado:</td><td class=\"value\">" + tramite.getNumeroRadicado() + "</td></tr>");
        html.append("<tr><td class=\"label\">Solicitante:</td><td class=\"value\">" + tramite.getNombreSolicitante() + "</td></tr>");
        html.append("<tr><td class=\"label\">Documento:</td><td class=\"value\">" + safeValue(tramite.getTipoDocumento()) + " " + tramite.getNumeroDocumento() + "</td></tr>");
        html.append("<tr><td class=\"label\">Lugar Expedición:</td><td class=\"value\">" + safeValue(tramite.getLugarExpedicionDocumento()) + "</td></tr>");
        html.append("<tr><td class=\"label\">Residencia:</td><td class=\"value\">" + safeValue(tramite.getDireccionResidencia()) + ", " + safeValue(tramite.getBarrioResidencia()) + "</td></tr>");
        html.append("<tr><td class=\"label\">Tipo Certificado:</td><td class=\"value\">" + tipoCert + "</td></tr>");
        html.append("</table>");
        html.append(observacionHtml);
        html.append("<div class=\"firmas\">");
        html.append("<div class=\"firma\"><div class=\"linea-firma\"></div><div class=\"nombre-firma\">" + verificador + "</div><div class=\"cargo\">Verificador de Trámites</div></div>");
        html.append("<div class=\"firma\"><div class=\"linea-firma\"></div><div class=\"nombre-firma\">" + alcalde + "</div><div class=\"cargo\">Alcalde Municipal</div></div>");
        html.append("</div>");
        html.append("<div class=\"fecha\">Cabuyaro, " + diasLetras + " días de " + mesLetras + " del año " + anoLetras + " (" + fechaFirma.getYear() + ")</div>");
        html.append("</body></html>");
        return html.toString();
    }

    private String safeNombreCompleto(Usuario usuario) {
        return usuario != null ? safeValue(usuario.getNombreCompleto()) : "[Nombre Usuario]";
    }

    private static String valor(Object val) { return val == null ? "" : val.toString(); }

    private static String valorMayusculas(String val) { return val == null ? "" : val.toUpperCase(); }

    private record PdfGeneracionResultado(byte[] contenidoPdf, String engine) {}

    private record CertBundle(PrivateKey privateKey, Certificate[] chain) {}
}

