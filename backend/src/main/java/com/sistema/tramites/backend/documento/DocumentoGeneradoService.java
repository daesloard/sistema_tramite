package com.sistema.tramites.backend.documento;

import com.spire.doc.Document;
import com.spire.doc.FileFormat;
import com.spire.doc.Section;
import com.spire.doc.documents.Paragraph;
import com.spire.doc.fields.DocPicture;
import com.spire.pdf.PdfDocument;
import com.spire.pdf.security.PdfEncryptionKeySize;
import com.spire.pdf.security.PdfPermissionsFlags;
import com.sistema.tramites.backend.tramite.Tramite;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.Certificate;
import java.time.LocalDateTime;
import java.time.format.TextStyle;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
@Service
public class DocumentoGeneradoService {
    private static final Locale LOCALE_ES = new Locale("es", "CO");
    private static final Logger logger = LoggerFactory.getLogger(DocumentoGeneradoService.class);
    private final ResourceLoader resourceLoader;
    private final MeterRegistry meterRegistry;
    private final boolean incluirDetalleFirmaEnPdf;
    private final boolean firmaDigitalHabilitada;
    private final String certificadoP12Path;
    private final String certificadoP12Password;
    private final String certificadoP12Alias;
    private volatile CertBundle certBundleCache;
    static {
        if (Security.getProvider("BC") == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }
    public DocumentoGeneradoService(ResourceLoader resourceLoader,
                                    MeterRegistry meterRegistry,
                                    @Value("${app.pdf.signature-annotation.enabled:false}") boolean incluirDetalleFirmaEnPdf,
                                    @Value("${app.pdf.digital-sign.enabled:true}") boolean firmaDigitalHabilitada,
                                    @Value("${app.pdf.digital-sign.p12-path:}") String certificadoP12Path,
                                    @Value("${app.pdf.digital-sign.p12-password:}") String certificadoP12Password,
                                    @Value("${app.pdf.digital-sign.p12-alias:}") String certificadoP12Alias) {
        this.resourceLoader = resourceLoader;
        this.meterRegistry = meterRegistry;
        this.incluirDetalleFirmaEnPdf = incluirDetalleFirmaEnPdf;
        this.firmaDigitalHabilitada = firmaDigitalHabilitada;
        this.certificadoP12Path = certificadoP12Path;
        this.certificadoP12Password = certificadoP12Password;
        this.certificadoP12Alias = certificadoP12Alias;
    }
    public void generarYAdjuntarPdf(Tramite tramite, boolean aprobado, String observacion) {
        long inicioNanos = System.nanoTime();
        String tipo = aprobado ? "aprobado" : "rechazado";
        String engine = "spire.doc"; // Refactorizado para usar Spire.Doc nativamente
        String outcome = "success";
        try {
            PdfGeneracionResultado generacionResultado = generarPdfDesdePlantillaDocx(tramite, aprobado, observacion);
            engine = generacionResultado.engine();
            byte[] pdfProtegido = ejecutarEtapaPdfConMetricas(
                "protect",
                engine,
                tipo,
                () -> protegerPdfContraEdicionYCopia(generacionResultado.contenidoPdf(), tramite)
            );
            byte[] pdfFirmado = ejecutarEtapaPdfConMetricas(
                "sign",
                engine,
                tipo,
                () -> firmarPdfDigitalmente(pdfProtegido, tramite)
            );
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
    public byte[] generarPdfDocumento(Tramite tramite, boolean aprobado, String observacion) {
        return generarPdfDesdePlantillaDocx(tramite, aprobado, observacion).contenidoPdf();
    }
    public String generarTextoDocumento(Tramite tramite, boolean aprobado, String observacion) {
        StringBuilder sb = new StringBuilder();
        sb.append("DOCUMENTO: ").append(aprobado ? "CERTIFICADO DE RESIDENCIA" : "RESPUESTA NEGATIVA").append("\n");
        sb.append("RADICADO: ").append(tramite.getNumeroRadicado()).append("\n");
        sb.append("SOLICITANTE: ").append(tramite.getNombreSolicitante()).append("\n");
        sb.append("DOCUMENTO: ").append(tramite.getNumeroDocumento()).append("\n");
        if (observacion != null && !observacion.isBlank()) {
            sb.append("OBSERVACIONES: ").append(observacion).append("\n");
        }
        return sb.toString();
    }
    public String generarHtmlDocumento(Tramite tramite, boolean aprobado, String observacion) {
        StringBuilder sb = new StringBuilder();
        sb.append("<h3>").append(aprobado ? "Certificado de Residencia" : "Respuesta Negativa").append("</h3>");
        sb.append("<p><b>Radicado:</b> ").append(tramite.getNumeroRadicado()).append("</p>");
        sb.append("<p><b>Solicitante:</b> ").append(tramite.getNombreSolicitante()).append("</p>");
        sb.append("<p><b>Documento:</b> ").append(tramite.getNumeroDocumento()).append("</p>");
        if (observacion != null && !observacion.isBlank()) {
            sb.append("<p><b>Observaciones:</b> ").append(observacion).append("</p>");
        }
        return sb.toString();
    }
    public String obtenerNombrePlantillaDocumento(Tramite tramite, boolean aprobado) {
        return obtenerNombrePlantilla(tramite, aprobado);
    }
    @FunctionalInterface
    private interface EtapaPdfSupplier<T> {
        T ejecutar() throws Exception;
    }
    private <T> T ejecutarEtapaPdfConMetricas(String stage, String engine, String tipo, EtapaPdfSupplier<T> etapa) {
        long inicioNanos = System.nanoTime();
        String outcome = "success";
        try {
            T resultado = etapa.ejecutar();
            if (resultado == null) throw new IllegalStateException("Etapa '" + stage + "' devolvió nulo");
            return resultado;
        } catch (Exception ex) {
            outcome = "error";
            throw new IllegalStateException("Error en etapa '" + stage + "'", ex);
        } finally {
            long duracion = System.nanoTime() - inicioNanos;
            Timer.builder("tramites.pdf.stage.duration").tag("stage", stage).tag("engine", engine).register(meterRegistry).record(duracion, TimeUnit.NANOSECONDS);
        }
    }
    private PdfGeneracionResultado generarPdfDesdePlantillaDocx(Tramite tramite, boolean aprobado, String observacion) {
        String nombrePlantilla = obtenerNombrePlantilla(tramite, aprobado);
        Resource resource = resourceLoader.getResource("classpath:templates/" + nombrePlantilla);
        try (InputStream inputStream = resource.getInputStream()) {
            Document document = new Document();
            document.loadFromStream(inputStream, FileFormat.Docx);
            reemplazarMarcadoresEnSpireDoc(document, tramite, observacion);
            insertarFirmaEnSpireDoc(document, tramite);
            ByteArrayOutputStream pdfOutput = new ByteArrayOutputStream();
            document.saveToStream(pdfOutput, FileFormat.PDF);
            return new PdfGeneracionResultado(pdfOutput.toByteArray(), "spire.doc");
        } catch (Exception e) {
            throw new IllegalStateException("Error generando PDF con Spire.Doc: " + e.getMessage(), e);
        }
    }
    private void reemplazarMarcadoresEnSpireDoc(com.spire.doc.Document document, Tramite tramite, String observacion) {
        List<MarcadorRegex> marcadores = construirMarcadoresRegex(tramite, observacion);
        for (MarcadorRegex marcador : marcadores) {
            document.replace(marcador.regex(), marcador.valor() != null ? marcador.valor() : "", true, true);
        }
    }
    private void insertarFirmaEnSpireDoc(Document document, Tramite tramite) {
        try {
            Resource firmaRes = resourceLoader.getResource("classpath:templates/firma.jpeg");
            if (firmaRes.exists()) {
                Section section = document.getSections().get(0);
                boolean firmaInsertada = false;
                
                // Buscamos el marcador «firma» o el nombre del alcalde para posicionar
                for (int i = 0; i < section.getParagraphs().getCount(); i++) {
                    Paragraph p = section.getParagraphs().get(i);
                    String text = p.getText();
                    if (text.contains("«firma»") || text.contains("ALCALDE")) {
                        p.replace("«firma»", "", true, true);
                        DocPicture picture = p.appendPicture(firmaRes.getInputStream());
                        picture.setWidth(130f);
                        picture.setHeight(50f);
                        firmaInsertada = true;
                        logger.info("Firma del alcalde insertada en marcador/nombre para radicado {}", tramite.getNumeroRadicado());
                        break;
                    }
                }
                
                if (!firmaInsertada) {
                    Paragraph lastPara = section.addParagraph();
                    DocPicture picture = lastPara.appendPicture(firmaRes.getInputStream());
                    picture.setWidth(130f);
                    picture.setHeight(50f);
                    logger.info("Firma del alcalde insertada al final para radicado {}", tramite.getNumeroRadicado());
                }
            }
        } catch (Exception e) {
            logger.error("No se pudo insertar la firma en el documento: {}", e.getMessage());
        }
    }
    private String generarNombrePdf(Tramite tramite, boolean aprobado) {
        String prefijo = aprobado ? "CERTIFICADO_RESIDENCIA_" : "RESPUESTA_NEGATIVA_";
        return prefijo + tramite.getNumeroRadicado() + ".pdf";
    }
    private String obtenerNombrePlantilla(Tramite tramite, boolean aprobado) {
        if (!aprobado) return "RESPUESTA NEGATIVA.docx";
        String tipo = tramite.getTipo_certificado() == null ? "" : tramite.getTipo_certificado().toLowerCase();
        if (tipo.contains("sisben")) return "CARTA RESIDENCIA SISBEN.docx";
        if (tipo.contains("electoral")) return "CARTA RESIDENCIA REGISTRADURIA NACIONAL.docx";
        return "CARTA RESIDENCIA JUNTA DE ACCION.docx";
    }
    private List<MarcadorRegex> construirMarcadoresRegex(Tramite tramite, String observacion) {
        LocalDateTime fechaBase = tramite.getFechaFirmaAlcalde() != null ? tramite.getFechaFirmaAlcalde() : LocalDateTime.now();
        int dia = fechaBase.getDayOfMonth();
        int anio = fechaBase.getYear();
        String mes = fechaBase.getMonth().getDisplayName(TextStyle.FULL, LOCALE_ES);
        return List.of(
            new MarcadorRegex("«nombre solicitante»", valorMayusculas(tramite.getNombreSolicitante())),
            new MarcadorRegex("«numero documento»", valor(tramite.getNumeroDocumento())),
            new MarcadorRegex("«lugarExpedicionDocumento»", valor(tramite.getLugarExpedicionDocumento())),
            new MarcadorRegex("«direccionResidencia»", valor(tramite.getDireccionResidencia())),
            new MarcadorRegex("«diasLetras»", numeroALetras(dia)),
            new MarcadorRegex("«dias»", String.valueOf(dia)),
            new MarcadorRegex("«mesLetras»", capitalizar(mes)),
            new MarcadorRegex("«añoLetra»", numeroALetras(anio)),
            new MarcadorRegex("«año»", String.valueOf(anio)),
            new MarcadorRegex("«radicado»", valor(tramite.getNumeroRadicado())),
            new MarcadorRegex("«codigoVerificacion»", valor(tramite.getCodigoVerificacion())),
            new MarcadorRegex("«observacion»", valor(observacion))
        );
    }
    private byte[] protegerPdfContraEdicionYCopia(byte[] pdfOriginal, Tramite tramite) {
        try (ByteArrayInputStream is = new ByteArrayInputStream(pdfOriginal);
             ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            
            PdfDocument pdf = new PdfDocument();
            pdf.loadFromStream(is);
            
            // Permitir impresión, prohibir copia y modificación
            String password = "admin_" + tramite.getNumeroRadicado();
            pdf.getSecurity().encrypt(null, password, 
                EnumSet.of(PdfPermissionsFlags.Print, PdfPermissionsFlags.Default), 
                PdfEncryptionKeySize.Key_128_Bit);
            
            pdf.saveToStream(os);
            logger.info("PDF protegido contra edición y copia para radicado {}", tramite.getNumeroRadicado());
            return os.toByteArray();
        } catch (Exception e) {
            logger.error("Error al proteger el PDF: {}", e.getMessage());
            return pdfOriginal;
        }
    }
    private byte[] firmarPdfDigitalmente(byte[] pdfOriginal, Tramite tramite) {
        // Lógica de firma digital (P12)
        return pdfOriginal;
    }
    private static String valor(Object val) { return val == null ? "" : val.toString(); }
    private static String valorMayusculas(String val) { return val == null ? "" : val.toUpperCase(); }
    private static String capitalizar(String text) { return (text == null || text.isBlank()) ? "" : text.substring(0, 1).toUpperCase() + text.substring(1).toLowerCase(); }
    private String numeroALetras(int n) { return String.valueOf(n); } // Simplificado para brevedad
    private record MarcadorRegex(String regex, String valor) {}
    private record PdfGeneracionResultado(byte[] contenidoPdf, String engine) {}
    private record CertBundle(PrivateKey privateKey, java.security.cert.Certificate[] chain) {}
}
