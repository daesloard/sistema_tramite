package com.sistema.tramites.backend;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.PdfStamper;
import com.lowagie.text.pdf.PdfWriter;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.docx4j.convert.out.pdf.PdfConversion;
import org.docx4j.convert.out.pdf.viaXSLFO.Conversion;
import org.docx4j.convert.out.pdf.viaXSLFO.PdfSettings;
import org.docx4j.fonts.IdentityPlusMapper;
import org.docx4j.fonts.Mapper;
import org.docx4j.fonts.PhysicalFont;
import org.docx4j.fonts.PhysicalFonts;
import org.docx4j.openpackaging.exceptions.Docx4JException;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFFooter;
import org.apache.poi.xwpf.usermodel.XWPFHeader;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.apache.poi.util.Units;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBookmark;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTHyperlink;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSpacing;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.text.Normalizer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class DocumentoGeneradoService {

    private static final Locale LOCALE_ES = new Locale("es", "CO");
    private static final Logger logger = LoggerFactory.getLogger(DocumentoGeneradoService.class);
    private static final java.math.BigInteger ESPACIO_UNO = java.math.BigInteger.valueOf(240L);
    private static final java.math.BigInteger ESPACIO_DOS = java.math.BigInteger.valueOf(480L);
    private static final java.math.BigInteger ESPACIO_TRES = java.math.BigInteger.valueOf(720L);
    private static final java.math.BigInteger ESPACIO_FIRMA = java.math.BigInteger.ZERO;
    private static final String MARCADOR_FIRMA_ANGULAR = "<<firma.jpeg>>";
    private static final String MARCADOR_FIRMA_GUILLEMET = "«firma.jpeg»";
    private static final int RECORTE_FIRMA_ANTES = 5;
    private static final int RECORTE_FIRMA_DESPUES = 4;
    private static final int RECORTE_PARRAFOS_VACIOS_ANTES = 4;
    private static final int RECORTE_PARRAFOS_VACIOS_DESPUES = 2;
    private static final String FUENTE_MAVEN_PRO = "Maven Pro";
    private static final String FUENTE_MAVEN_PRO_ALIAS = "MavenPro";
    private static final String RECURSO_MAVEN_PRO_TTF = "classpath:fonts/MavenPro[wght].ttf";

    private final ResourceLoader resourceLoader;
    private final MeterRegistry meterRegistry;
    private final boolean usarSpire;
    private final boolean usarLibreOffice;
    private final boolean usarDocx4j;
    private final boolean usarGotenberg;
    private final String gotenbergUrl;
    private final int gotenbergTimeoutSegundos;
    private final boolean gotenbergFallbackHabilitado;
    private final boolean forzarFuenteMavenPro;
    private final boolean ajustarEspaciadoPlantillaPdf;
    private final boolean recortarParrafosVaciosFirma;
    private final boolean compactarLayoutFirma;
    private final int firmaAnchoPx;
    private final int firmaAltoPx;
    private final boolean incluirDetalleFirmaEnPdf;
    private final boolean firmaDigitalHabilitada;
    private final String certificadoP12Path;
    private final String certificadoP12Password;
    private final String certificadoP12Alias;
    private final HttpClient gotenbergHttpClient;
    private volatile CertBundle certBundleCache;
    private volatile PhysicalFont fuenteMavenProCache;
    private volatile PhysicalFont fuenteMavenProBoldCache;

    static {
        if (Security.getProvider("BC") == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    public DocumentoGeneradoService(ResourceLoader resourceLoader,
                                    MeterRegistry meterRegistry,
                                    @Value("${app.pdf.use-spire:false}") boolean usarSpire,
                                    @Value("${app.pdf.use-libreoffice:false}") boolean usarLibreOffice,
                                    @Value("${app.pdf.use-docx4j:true}") boolean usarDocx4j,
                                    @Value("${app.pdf.gotenberg.enabled:false}") boolean usarGotenberg,
                                    @Value("${app.pdf.gotenberg.url:}") String gotenbergUrl,
                                    @Value("${app.pdf.gotenberg.timeout-seconds:25}") int gotenbergTimeoutSegundos,
                                    @Value("${app.pdf.gotenberg.fallback-enabled:true}") boolean gotenbergFallbackHabilitado,
                                    @Value("${app.pdf.enforce-maven-pro-font:false}") boolean forzarFuenteMavenPro,
                                    @Value("${app.pdf.adjust-template-spacing:false}") boolean ajustarEspaciadoPlantillaPdf,
                                    @Value("${app.pdf.signature-trim-empty-paragraphs:false}") boolean recortarParrafosVaciosFirma,
                                    @Value("${app.pdf.signature-compact-layout:false}") boolean compactarLayoutFirma,
                                    @Value("${app.pdf.signature-image-width-px:170}") int firmaAnchoPx,
                                    @Value("${app.pdf.signature-image-height-px:52}") int firmaAltoPx,
                                    @Value("${app.pdf.signature-annotation.enabled:false}") boolean incluirDetalleFirmaEnPdf,
                                    @Value("${app.pdf.digital-sign.enabled:true}") boolean firmaDigitalHabilitada,
                                    @Value("${app.pdf.digital-sign.p12-path:}") String certificadoP12Path,
                                    @Value("${app.pdf.digital-sign.p12-password:}") String certificadoP12Password,
                                    @Value("${app.pdf.digital-sign.p12-alias:}") String certificadoP12Alias) {
        this.resourceLoader = resourceLoader;
        this.meterRegistry = meterRegistry;
                        this.usarSpire = usarSpire;
        this.usarLibreOffice = usarLibreOffice;
        this.usarDocx4j = usarDocx4j;
        this.usarGotenberg = usarGotenberg;
        this.gotenbergUrl = gotenbergUrl;
        this.gotenbergTimeoutSegundos = gotenbergTimeoutSegundos;
        this.gotenbergFallbackHabilitado = gotenbergFallbackHabilitado;
        this.forzarFuenteMavenPro = forzarFuenteMavenPro;
        this.ajustarEspaciadoPlantillaPdf = ajustarEspaciadoPlantillaPdf;
        this.recortarParrafosVaciosFirma = recortarParrafosVaciosFirma;
        this.compactarLayoutFirma = compactarLayoutFirma;
        this.firmaAnchoPx = Math.max(40, firmaAnchoPx);
        this.firmaAltoPx = Math.max(20, firmaAltoPx);
        this.incluirDetalleFirmaEnPdf = incluirDetalleFirmaEnPdf;
        this.firmaDigitalHabilitada = firmaDigitalHabilitada;
        this.certificadoP12Path = certificadoP12Path;
        this.certificadoP12Password = certificadoP12Password;
        this.certificadoP12Alias = certificadoP12Alias;
        this.gotenbergHttpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(Math.max(3, gotenbergTimeoutSegundos)))
            .build();
    }

    public void generarYAdjuntarPdf(Tramite tramite, boolean aprobado, String observacion) {
        long inicioNanos = System.nanoTime();
        String tipo = aprobado ? "aprobado" : "rechazado";
        String engine = "unknown";
        String outcome = "success";

        try {
            PdfGeneracionResultado generacionResultado = generarPdfDocumentoConMetadatos(tramite, aprobado, observacion);
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
            throw ex;
        } finally {
            long duracionNanos = System.nanoTime() - inicioNanos;
            Timer.builder("tramites.pdf.generation.duration")
                    .description("Duracion de generacion de PDF")
                    .tag("engine", engine)
                    .tag("outcome", outcome)
                    .tag("tipo", tipo)
                    .register(meterRegistry)
                    .record(duracionNanos, TimeUnit.NANOSECONDS);

            Counter.builder("tramites.pdf.generation.total")
                    .description("Total de ejecuciones de generacion de PDF")
                    .tag("engine", engine)
                    .tag("outcome", outcome)
                    .tag("tipo", tipo)
                    .register(meterRegistry)
                    .increment();
        }
    }

    public byte[] generarPdfDocumento(Tramite tramite, boolean aprobado, String observacion) {
        return generarPdfDocumentoConMetadatos(tramite, aprobado, observacion).contenidoPdf();
    }

    private PdfGeneracionResultado generarPdfDocumentoConMetadatos(Tramite tramite, boolean aprobado, String observacion) {
        return generarPdfDesdePlantillaDocx(tramite, aprobado, observacion);
    }

    @FunctionalInterface
    private interface EtapaPdfSupplier<T> {
        T ejecutar() throws Exception;
    }

    private <T> T ejecutarEtapaPdfConMetricas(String stage,
                                              String engine,
                                              String tipo,
                                              EtapaPdfSupplier<T> etapa) {
        long inicioNanos = System.nanoTime();
        String outcome = "success";
        String engineTag = (engine == null || engine.isBlank()) ? "unknown" : engine;

        try {
            T resultado = etapa.ejecutar();
            if (resultado == null) {
                throw new IllegalStateException("La etapa PDF '" + stage + "' devolvió resultado nulo");
            }
            return resultado;
        } catch (Exception ex) {
            outcome = "error";
            Counter.builder("tramites.pdf.stage.errors")
                    .description("Errores por etapa en la generacion de PDF")
                    .tag("stage", stage)
                    .tag("engine", engineTag)
                    .tag("tipo", tipo)
                    .register(meterRegistry)
                    .increment();

            if (ex instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("Fallo en la etapa PDF '" + stage + "'", ex);
        } finally {
            long duracionNanos = System.nanoTime() - inicioNanos;
            Timer.builder("tramites.pdf.stage.duration")
                    .description("Duracion por etapa en la generacion de PDF")
                    .tag("stage", stage)
                    .tag("engine", engineTag)
                    .tag("outcome", outcome)
                    .tag("tipo", tipo)
                    .register(meterRegistry)
                    .record(duracionNanos, TimeUnit.NANOSECONDS);

            Counter.builder("tramites.pdf.stage.total")
                    .description("Total de ejecuciones por etapa en la generacion de PDF")
                    .tag("stage", stage)
                    .tag("engine", engineTag)
                    .tag("outcome", outcome)
                    .tag("tipo", tipo)
                    .register(meterRegistry)
                    .increment();
        }
    }

    public String generarTextoDocumento(Tramite tramite, boolean aprobado, String observacion) {
        String plantilla = obtenerNombrePlantilla(tramite, aprobado);
        String texto = leerTextoPlantilla(plantilla);
        return reemplazarMarcadores(texto, tramite, observacion);
    }

        public String generarHtmlDocumento(Tramite tramite, boolean aprobado, String observacion) {
                LocalDateTime fechaBase = tramite.getFechaFirmaAlcalde() != null
                                ? tramite.getFechaFirmaAlcalde()
                                : LocalDateTime.now();

                int dia = fechaBase.getDayOfMonth();
                int anio = fechaBase.getYear();
                String mes = capitalizar(fechaBase.getMonth().getDisplayName(TextStyle.FULL, LOCALE_ES));
                String consecutivo = valor(tramite.getConsecutivoVerificador());
                String nombre = escapeHtml(valorMayusculas(tramite.getNombreSolicitante()));
                String tipoDoc = escapeHtml(valor(tramite.getTipoDocumento()));
                String numeroDoc = escapeHtml(valor(tramite.getNumeroDocumento()));
                String lugarExp = escapeHtml(valor(tramite.getLugarExpedicionDocumento()));
                String direccion = escapeHtml(valorDireccionCertificado(tramite.getDireccionResidencia()));
                String barrio = escapeHtml(valor(tramite.getBarrioResidencia()));
                String obs = escapeHtml(valor(observacion));

                String cuerpo;
                if (!aprobado) {
                        cuerpo = """
                                <p><strong>Asunto:</strong> Respuesta a solicitud certificado de residencia.</p>
                                <p>Señor(a): <strong>%s</strong>, identificado(a) con %s No. <strong>%s</strong> expedida en <strong>%s</strong>.</p>
                                <p>Una vez revisadas las fuentes oficiales de información, este despacho informa que <strong>no es viable</strong> expedir el certificado solicitado.</p>
                                <p><strong>Motivo:</strong> %s</p>
                                <p>Cordialmente,</p>
                        """.formatted(nombre, tipoDoc, numeroDoc, lugarExp, obs.isBlank() ? "No cumple con los criterios de validación vigentes." : obs);
                } else {
                        String fuente = obtenerFuenteSegunTipo(tramite);
                        cuerpo = """
                                <p>Que el(la) señor(a) <strong>%s</strong>, identificado(a) con %s No. <strong>%s</strong> expedida en <strong>%s</strong>, figura en la base de datos de <strong>%s</strong>, correspondiente a esta municipalidad.</p>
                                <p>Se encuentra domiciliado(a) en la dirección <strong>%s</strong>, barrio <strong>%s</strong>.</p>
                                <p>En consecuencia, tiene el carácter de habitante del Municipio de Cabuyaro (Meta), conforme a la normatividad vigente.</p>
                                <p>Se expide para los fines pertinentes, a los <strong>%s</strong> (%s) días del mes de <strong>%s</strong> del año <strong>%s</strong> (%s).</p>
                                <p>Cordialmente,</p>
                        """.formatted(
                                        nombre,
                                        tipoDoc,
                                        numeroDoc,
                                        lugarExp,
                                        escapeHtml(fuente),
                                        direccion,
                                        barrio,
                                        numeroALetras(dia),
                                        dia,
                                        mes,
                                        numeroALetras(anio),
                                        anio
                        );
                }

                                return """
                        <!DOCTYPE html>
                        <html lang="es">
                        <head>
                                                        <meta charset="UTF-8" />
                                                        <meta name="viewport" content="width=device-width, initial-scale=1.0" />
                                                        <link rel="preconnect" href="https://fonts.googleapis.com">
                                                        <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
                                                        <link href="https://fonts.googleapis.com/css2?family=Maven+Pro:wght@400;500;700;800&display=swap" rel="stylesheet">
                            <style>
                                                                * { box-sizing: border-box; }
                                                                body {
                                                                    font-family: 'Maven Pro', Arial, sans-serif;
                                                                    margin: 28px;
                                                                    color: #111;
                                                                    background: #fff;
                                                                    line-height: 1.45;
                                                                }
                                                                .header {
                                                                    border: 1px solid #4b5563;
                                                                    display: grid;
                                                                    grid-template-columns: 160px 1fr 190px;
                                                                    min-height: 112px;
                                                                    margin-bottom: 14px;
                                                                }
                                                                .logo {
                                                                    border-right: 1px solid #4b5563;
                                                                    display:flex;
                                                                    align-items:center;
                                                                    justify-content:center;
                                                                    font-weight:700;
                                                                    font-size: 22px;
                                                                    line-height: 1.1;
                                                                    text-align:center;
                                                                    color: #1f2937;
                                                                }
                                                                .centro {
                                                                    padding: 8px;
                                                                    text-align: center;
                                                                    font-size: 13px;
                                                                    border-right: 1px solid #4b5563;
                                                                    display:flex;
                                                                    flex-direction:column;
                                                                    justify-content:center;
                                                                    gap:6px;
                                                                }
                                .centro .titulo { font-weight: bold; }
                                                                .meta {
                                                                    font-size: 12px;
                                                                    padding: 8px;
                                                                    line-height: 1.35;
                                                                    border-left: 1px solid #4b5563;
                                                                }
                                                                .meta strong { font-weight: 800; }
                                                                .consecutivo {
                                                                    margin-top: 14px;
                                                                    margin-bottom: 18px;
                                                                    font-size: 14px;
                                                                    font-weight: 700;
                                                                }
                                                                h1 {
                                                                    text-align:center;
                                                                    font-size: 34px;
                                                                    margin-top: 22px;
                                                                    margin-bottom: 0;
                                                                    font-weight: 800;
                                                                }
                                                                h2 {
                                                                    text-align:center;
                                                                    font-size: 28px;
                                                                    margin-top: 14px;
                                                                    margin-bottom: 30px;
                                                                    font-weight: 800;
                                                                }
                                                                .cuerpo {
                                                                    margin-top: 24px;
                                                                    font-size: 31px;
                                                                    line-height: 1.75;
                                                                    text-align: justify;
                                                                }
                                                                .cuerpo p { margin: 0 0 34px 0; }
                                                                .firma {
                                                                    margin-top: 68px;
                                                                    font-size: 32px;
                                                                }
                                                                .linea { width: 380px; border-top: 1px solid #000; margin-top: 24px; margin-bottom: 10px; }
                                                                .pie {
                                                                    margin-top: 28px;
                                                                    font-size: 20px;
                                                                    color: #222;
                                                                    border-top: 1px solid #4b5563;
                                                                    padding-top: 12px;
                                                                }
                            </style>
                        </head>
                        <body>
                            <div class="header">
                                <div class="logo">Alcaldía de<br/>Cabuyaro</div>
                                <div class="centro">
                                    <div class="titulo">DEPARTAMENTO DEL META<br/>MUNICIPIO DE CABUYARO</div>
                                    <div>NIT. 892099232-4</div>
                                    <div><strong>DESPACHO ALCALDE</strong></div>
                                    <div style="border-top:1px solid #666; margin-top:6px; padding-top:4px;"><strong>COMUNICACIONES OFICIALES</strong></div>
                                </div>
                                <div class="meta">CÓDIGO IJRD-100.09.01.<br/>VERSIÓN: 1<br/>Resolución 533/2018<br/>Página 1 de 1</div>
                            </div>

                            <div class="consecutivo"><strong>No°:</strong> %s</div>

                            <h1>EL SUSCRITO ALCALDE MUNICIPAL DE CABUYARO (META)</h1>
                            <h2>HACE CONSTAR:</h2>

                            <div class="cuerpo">
                                %s
                            </div>

                            <div class="firma">
                                <div class="linea"></div>
                                <div><strong>Alcalde Municipal</strong></div>
                            </div>

                            <div class="pie">Proy. y Elab.: Verificador Municipal</div>
                        </body>
                        </html>
                        """.formatted(escapeHtml(consecutivo), cuerpo);
        }

    public String obtenerNombrePlantillaDocumento(Tramite tramite, boolean aprobado) {
        return obtenerNombrePlantilla(tramite, aprobado);
    }

    private String obtenerNombrePlantilla(Tramite tramite, boolean aprobado) {
        if (!aprobado) {
            return "RESPUESTA NEGATIVA.docx";
        }

        String tipo = tramite.getTipo_certificado() == null ? "" : tramite.getTipo_certificado().toLowerCase();
        if (tipo.contains("sisben")) {
            return "CARTA RESIDENCIA SISBEN.docx";
        }
        if (tipo.contains("electoral")) {
            return "CARTA RESIDENCIA REGISTRADURIA NACIONAL.docx";
        }
        return "CARTA RESIDENCIA JUNTA DE ACCION.docx";
    }

    private String leerTextoPlantilla(String nombrePlantilla) {
        Resource resource = resourceLoader.getResource("classpath:templates/" + nombrePlantilla);
        try (InputStream inputStream = resource.getInputStream();
             XWPFDocument document = new XWPFDocument(inputStream);
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return extractor.getText();
        } catch (IOException e) {
            throw new IllegalStateException("No fue posible leer la plantilla: " + nombrePlantilla, e);
        }
    }

    private String reemplazarMarcadores(String contenido, Tramite tramite, String observacion) {
        String resultado = contenido;
        List<MarcadorRegex> marcadores = construirMarcadoresRegex(tramite, observacion);
        for (MarcadorRegex marcador : marcadores) {
            resultado = reemplazarRegex(resultado, marcador.regex(), marcador.valor());
        }

        return resultado;
    }

    private List<MarcadorRegex> construirMarcadoresRegex(Tramite tramite, String observacion) {
        LocalDateTime fechaBase = tramite.getFechaFirmaAlcalde() != null
                ? tramite.getFechaFirmaAlcalde()
                : LocalDateTime.now();

        int dia = fechaBase.getDayOfMonth();
        int anio = fechaBase.getYear();
        String mes = fechaBase.getMonth().getDisplayName(TextStyle.FULL, LOCALE_ES);
        String fechaFirmaTexto = fechaBase.format(DateTimeFormatter.ofPattern("dd/MM/yyyy", LOCALE_ES));

        return List.of(
                new MarcadorRegex("«\\s*nombre\\s*s\\s*olicitante\\s*»|«\\s*nombre_colicitante\\s*»", valorMayusculas(tramite.getNombreSolicitante())),
                new MarcadorRegex("«\\s*numero\\s*d\\s*ocumento\\s*»|«\\s*numero_documento\\s*»", valor(tramite.getNumeroDocumento())),
                new MarcadorRegex("«\\s*lugarExpedicionDocumento\\s*»|«\\s*lugar_expedicion_documento\\s*»", valor(tramite.getLugarExpedicionDocumento())),
                new MarcadorRegex("«\\s*direccionResidencia\\s*»", valorDireccionCertificado(tramite.getDireccionResidencia())),
                new MarcadorRegex("«\\s*diasLetras\\s*»", numeroALetras(dia)),
                new MarcadorRegex("«\\s*dias\\s*»", String.valueOf(dia)),
                new MarcadorRegex("«\\s*mesLetras\\s*»", capitalizar(mes)),
                new MarcadorRegex("«\\s*añoLetra\\s*»", numeroALetras(anio)),
                new MarcadorRegex("«\\s*año\\s*»", String.valueOf(anio)),
                new MarcadorRegex("«\\s*alcalde\\s*»", nombreAlcaldePlantilla(tramite)),
                new MarcadorRegex("«\\s*verificador\\s*»", nombreVerificadorPlantilla(tramite)),
                new MarcadorRegex("«\\s*tipo_documento\\s*»|«\\s*tipoDocumento\\s*»|<<\\s*tipo_documento\\s*>>|<<\\s*tipoDocumento\\s*>>", valor(tramite.getTipoDocumento())),
                new MarcadorRegex("«\\s*fechaFrima\\s*»|«\\s*fechaFirma\\s*»|<<\\s*fechaFrima\\s*>>|<<\\s*fechaFirma\\s*>>", fechaFirmaTexto),
                new MarcadorRegex("«\\s*observacion\\s*»", valor(observacion)),
                new MarcadorRegex("«\\s*consecutivo\\s*»|«\\s*consecutivo_verificador\\s*»", valor(tramite.getConsecutivoVerificador())),
                new MarcadorRegex("«\\s*numeroRadicado\\s*»|«\\s*numeroRadico\\s*»|«\\s*numero_radicado\\s*»|«\\s*radicado\\s*»", valor(tramite.getNumeroRadicado())),
                new MarcadorRegex("«\\s*codigo_verificacion\\s*»|«\\s*codigoVerificacion\\s*»", valor(tramite.getCodigoVerificacion())),
                new MarcadorRegex("«\\s*hash_documento\\s*»|«\\s*hashDocumento\\s*»|«\\s*hash_firma\\s*»", generarHashVisibleFirma(tramite)),
                new MarcadorRegex("«\\s*leyenda_firma_digital\\s*»|«\\s*firma_digital_leyenda\\s*»", generarLeyendaFirmaDigital(tramite))
        );
    }

    private String valorDireccionCertificado(String direccionResidencia) {
        String direccion = valor(direccionResidencia);
        if (direccion.isBlank()) {
            return "";
        }

        String normalizada = Normalizer.normalize(direccion, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .trim()
                .toLowerCase(LOCALE_ES);

        if ("no aplica".equals(normalizada) || "n/a".equals(normalizada) || "na".equals(normalizada)) {
            return "";
        }

        return direccion;
    }

    private byte[] convertirTextoAPdf(String texto) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            Document document = new Document();
            PdfWriter.getInstance(document, outputStream);
            document.open();

            Font font = FontFactory.getFont(FontFactory.HELVETICA, 11);
            for (String linea : texto.split("\\r?\\n")) {
                if (linea == null || linea.isBlank()) {
                    document.add(new Paragraph(" "));
                } else {
                    document.add(new Paragraph(linea.trim(), font));
                }
            }

            document.close();
            return outputStream.toByteArray();
        } catch (DocumentException e) {
            throw new IllegalStateException("No fue posible generar el PDF", e);
        }
    }

    private byte[] protegerPdfContraEdicionYCopia(byte[] pdfOriginal, Tramite tramite) {
        if (pdfOriginal == null || pdfOriginal.length == 0) {
            return pdfOriginal;
        }

        String radicado = valor(tramite != null ? tramite.getNumeroRadicado() : null);
        String ownerPassword = "certificado-" + (radicado.isBlank() ? "cabuyaro" : radicado) + "-" + System.currentTimeMillis();

        int permisos = PdfWriter.ALLOW_PRINTING | PdfWriter.ALLOW_DEGRADED_PRINTING;

        try {
            PdfReader reader = new PdfReader(pdfOriginal);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            PdfStamper stamper = new PdfStamper(reader, outputStream);
            stamper.setEncryption(null, ownerPassword.getBytes(StandardCharsets.UTF_8), permisos, true);
            stamper.close();
            reader.close();
            return outputStream.toByteArray();
        } catch (Exception ex) {
            logger.warn("No fue posible aplicar protección anti-copia/edición al PDF: {}", ex.getMessage());
            return pdfOriginal;
        }
    }

    private byte[] firmarPdfDigitalmente(byte[] pdfOriginal, Tramite tramite) {
        if (!firmaDigitalHabilitada || pdfOriginal == null || pdfOriginal.length == 0) {
            return pdfOriginal;
        }

        try {
            CertBundle certBundle = cargarCertificadoFirmante();

                com.itextpdf.text.pdf.PdfReader reader = new com.itextpdf.text.pdf.PdfReader(pdfOriginal);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                com.itextpdf.text.pdf.PdfStamper stamper = com.itextpdf.text.pdf.PdfStamper.createSignature(reader, outputStream, '\0', null, true);

                com.itextpdf.text.pdf.PdfSignatureAppearance appearance = stamper.getSignatureAppearance();
            appearance.setReason("Certificado de residencia firmado digitalmente");
            appearance.setLocation("Cabuyaro, Meta");
                appearance.setCertificationLevel(com.itextpdf.text.pdf.PdfSignatureAppearance.CERTIFIED_NO_CHANGES_ALLOWED);
            appearance.setSignDate(java.util.Calendar.getInstance());

                com.itextpdf.text.pdf.security.ExternalDigest digest = new com.itextpdf.text.pdf.security.BouncyCastleDigest();
                com.itextpdf.text.pdf.security.ExternalSignature signature =
                    new com.itextpdf.text.pdf.security.PrivateKeySignature(certBundle.privateKey(), com.itextpdf.text.pdf.security.DigestAlgorithms.SHA256, "BC");

                com.itextpdf.text.pdf.security.MakeSignature.signDetached(
                    appearance,
                    digest,
                    signature,
                    certBundle.chain(),
                    null,
                    null,
                    null,
                    0,
                    com.itextpdf.text.pdf.security.MakeSignature.CryptoStandard.CMS
            );

            reader.close();
            return outputStream.toByteArray();
        } catch (Exception ex) {
            logger.warn("No fue posible aplicar firma digital criptográfica al PDF: {}", ex.getMessage());
            return pdfOriginal;
        }
    }

    private CertBundle cargarCertificadoFirmante() throws Exception {
        CertBundle cache = certBundleCache;
        if (cache != null) {
            return cache;
        }

        synchronized (this) {
            if (certBundleCache != null) {
                return certBundleCache;
            }

        if (certificadoP12Path != null && !certificadoP12Path.isBlank()) {
            Path path = Paths.get(certificadoP12Path.trim());
            if (Files.exists(path)) {
                certBundleCache = cargarDesdePkcs12(path);
                return certBundleCache;
            }
            logger.warn("Ruta de certificado .p12 no encontrada: {}. Se usará certificado autofirmado temporal.", path);
        }

            certBundleCache = generarCertificadoAutofirmadoTemporal();
            return certBundleCache;
        }
    }

    private CertBundle cargarDesdePkcs12(Path path) throws Exception {
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        char[] password = certificadoP12Password != null ? certificadoP12Password.toCharArray() : new char[0];

        try (InputStream input = Files.newInputStream(path)) {
            keyStore.load(input, password);
        }

        String alias = (certificadoP12Alias != null && !certificadoP12Alias.isBlank())
                ? certificadoP12Alias.trim()
                : null;

        if (alias == null || !keyStore.isKeyEntry(alias)) {
            var aliases = keyStore.aliases();
            while (aliases.hasMoreElements()) {
                String current = aliases.nextElement();
                if (keyStore.isKeyEntry(current)) {
                    alias = current;
                    break;
                }
            }
        }

        if (alias == null) {
            throw new IllegalStateException("No se encontró clave privada en el certificado .p12");
        }

        PrivateKey privateKey = (PrivateKey) keyStore.getKey(alias, password);
        Certificate[] chain = keyStore.getCertificateChain(alias);
        if (privateKey == null || chain == null || chain.length == 0) {
            throw new IllegalStateException("Certificado .p12 inválido: falta clave privada o cadena de certificados");
        }

        return new CertBundle(privateKey, chain);
    }

    private CertBundle generarCertificadoAutofirmadoTemporal() throws GeneralSecurityException {
        try {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
            keyPairGenerator.initialize(2048);
            KeyPair keyPair = keyPairGenerator.generateKeyPair();

            X500Name dnName = new X500Name("CN=Alcaldia Cabuyaro, O=Municipio de Cabuyaro, C=CO");
            BigInteger serial = BigInteger.valueOf(System.currentTimeMillis());
            Date notBefore = new Date();
            Date notAfter = Date.from(LocalDateTime.now().plusYears(2).atZone(ZoneId.systemDefault()).toInstant());

            X509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
                    dnName,
                    serial,
                    notBefore,
                    notAfter,
                    dnName,
                    keyPair.getPublic()
            );

            ContentSigner contentSigner = new JcaContentSignerBuilder("SHA256withRSA")
                    .setProvider("BC")
                    .build(keyPair.getPrivate());

            X509CertificateHolder holder = certBuilder.build(contentSigner);
            X509Certificate certificate = new JcaX509CertificateConverter()
                    .setProvider("BC")
                    .getCertificate(holder);

            return new CertBundle(keyPair.getPrivate(), new Certificate[]{certificate});
        } catch (Exception e) {
            throw new GeneralSecurityException("No fue posible generar certificado autofirmado temporal", e);
        }
    }

    private record CertBundle(PrivateKey privateKey, Certificate[] chain) {
    }

    private PdfGeneracionResultado generarPdfDesdePlantillaDocx(Tramite tramite, boolean aprobado, String observacion) {
        String nombrePlantilla = obtenerNombrePlantilla(tramite, aprobado);
        Resource resource = resourceLoader.getResource("classpath:templates/" + nombrePlantilla);

        try (InputStream inputStream = resource.getInputStream();
             XWPFDocument wordDoc = new XWPFDocument(inputStream)) {

            reemplazarMarcadoresEnDocumento(wordDoc, tramite, observacion);
            insertarFirmaEnDocumento(wordDoc, tramite);
            if (forzarFuenteMavenPro) {
                aplicarFuenteMavenPro(wordDoc);
            }
            normalizarIdsInternosParaPdf(wordDoc);
            if (ajustarEspaciadoPlantillaPdf) {
                preservarEspaciadoPlantillaParaPdf(wordDoc);
            }
            String tipo = aprobado ? "aprobado" : "rechazado";
            return convertirDocxConPlantillaAPdf(wordDoc, tipo);
        } catch (IOException e) {
            throw new IllegalStateException("No fue posible generar PDF desde plantilla DOCX", e);
        }
    }

    private void aplicarFuenteMavenPro(XWPFDocument document) {
        if (document == null) {
            return;
        }

        boolean mavenProBoldDisponible = resolverFuenteMavenProBold() != null;

        for (XWPFParagraph paragraph : document.getParagraphs()) {
            aplicarFuenteMavenProEnParrafo(paragraph, mavenProBoldDisponible);
        }

        for (XWPFTable table : document.getTables()) {
            aplicarFuenteMavenProEnTabla(table, mavenProBoldDisponible);
        }

        for (XWPFHeader header : document.getHeaderList()) {
            for (XWPFParagraph paragraph : header.getParagraphs()) {
                aplicarFuenteMavenProEnParrafo(paragraph, mavenProBoldDisponible);
            }
            for (XWPFTable table : header.getTables()) {
                aplicarFuenteMavenProEnTabla(table, mavenProBoldDisponible);
            }
        }

        for (XWPFFooter footer : document.getFooterList()) {
            for (XWPFParagraph paragraph : footer.getParagraphs()) {
                aplicarFuenteMavenProEnParrafo(paragraph, mavenProBoldDisponible);
            }
            for (XWPFTable table : footer.getTables()) {
                aplicarFuenteMavenProEnTabla(table, mavenProBoldDisponible);
            }
        }
    }

    private void aplicarFuenteMavenProEnTabla(XWPFTable table, boolean mavenProBoldDisponible) {
        for (XWPFTableRow row : table.getRows()) {
            for (XWPFTableCell cell : row.getTableCells()) {
                for (XWPFParagraph paragraph : cell.getParagraphs()) {
                    aplicarFuenteMavenProEnParrafo(paragraph, mavenProBoldDisponible);
                }
                for (XWPFTable nested : cell.getTables()) {
                    aplicarFuenteMavenProEnTabla(nested, mavenProBoldDisponible);
                }
            }
        }
    }

    private void aplicarFuenteMavenProEnParrafo(XWPFParagraph paragraph, boolean mavenProBoldDisponible) {
        if (paragraph == null || paragraph.getRuns() == null) {
            return;
        }
        for (XWPFRun run : paragraph.getRuns()) {
            if (run != null) {
                // Forzar Maven Pro en todos los runs, incluidos los de negrita,
                // para garantizar uniformidad tipográfica en aprobados y rechazados.
                run.setFontFamily(FUENTE_MAVEN_PRO);
            }
        }
    }

    private void preservarEspaciadoPlantillaParaPdf(XWPFDocument document) {
        int indiceParrafoPrincipal = 0;
        for (XWPFParagraph paragraph : document.getParagraphs()) {
            if (aplicarEspaciadoParrafoPrincipal(paragraph, indiceParrafoPrincipal)) {
                indiceParrafoPrincipal++;
            }
        }

        for (XWPFTable table : document.getTables()) {
            ajustarTablaParaPdf(table);
        }

        for (XWPFHeader header : document.getHeaderList()) {
            for (XWPFParagraph paragraph : header.getParagraphs()) {
                ajustarParrafoParaPdf(paragraph);
            }
            for (XWPFTable table : header.getTables()) {
                ajustarTablaParaPdf(table);
            }
        }

        for (XWPFFooter footer : document.getFooterList()) {
            for (XWPFParagraph paragraph : footer.getParagraphs()) {
                ajustarParrafoParaPdf(paragraph);
            }
            for (XWPFTable table : footer.getTables()) {
                ajustarTablaParaPdf(table);
            }
        }
    }

    private void ajustarTablaParaPdf(XWPFTable table) {
        for (XWPFTableRow row : table.getRows()) {
            for (XWPFTableCell cell : row.getTableCells()) {
                for (XWPFParagraph paragraph : cell.getParagraphs()) {
                    ajustarParrafoParaPdf(paragraph);
                }
                for (XWPFTable nested : cell.getTables()) {
                    ajustarTablaParaPdf(nested);
                }
            }
        }
    }

    private void ajustarParrafoParaPdf(XWPFParagraph paragraph) {
        if (paragraph == null) {
            return;
        }

        if (paragraph.getCTP() == null) {
            return;
        }

        var pPr = paragraph.getCTP().isSetPPr() ? paragraph.getCTP().getPPr() : null;
        if (pPr == null || !pPr.isSetSpacing()) {
            return;
        }

        CTSpacing spacing = pPr.getSpacing();
        if (spacing == null) {
            return;
        }

        if (spacing.isSetAfter()) {
            Object afterObj = spacing.getAfter();
            if (afterObj instanceof java.math.BigInteger afterBigInteger) {
                spacing.setAfter(afterBigInteger);
            }
        }
        if (spacing.isSetBefore()) {
            Object beforeObj = spacing.getBefore();
            if (beforeObj instanceof java.math.BigInteger beforeBigInteger) {
                spacing.setBefore(beforeBigInteger);
            }
        }
    }

    private boolean aplicarEspaciadoParrafoPrincipal(XWPFParagraph paragraph, int indiceParrafoPrincipal) {
        if (paragraph == null) {
            return false;
        }

        String texto = paragraph.getText() == null ? "" : paragraph.getText().trim();
        if (texto.isBlank()) {
            return false;
        }

        if (paragraph.getCTP() == null) {
            return true;
        }

        var pPr = paragraph.getCTP().isSetPPr() ? paragraph.getCTP().getPPr() : paragraph.getCTP().addNewPPr();
        CTSpacing spacing = pPr.isSetSpacing() ? pPr.getSpacing() : pPr.addNewSpacing();

        String textoLower = texto.toLowerCase(LOCALE_ES);
        boolean esParrafoSuscrito = textoLower.contains("el suscrito alcalde");
        if (esParrafoSuscrito) {
            // Ajuste visual solicitado: +1 espacio antes del encabezado y -1 espacio debajo.
            spacing.setBefore(ESPACIO_TRES);
            spacing.setAfter(ESPACIO_UNO);
            return true;
        }

        boolean esHaceConstar = textoLower.contains("hace constar");
        if (esHaceConstar) {
            // Ajuste visual solicitado: reducir separación antes y después de "HACE CONSTAR".
            spacing.setBefore(java.math.BigInteger.ZERO);
            spacing.setAfter(java.math.BigInteger.ZERO);
            return true;
        }

        boolean esLineaFirma = textoLower.contains("alcalde municipal");
        if (esLineaFirma) {
            spacing.setBefore(ESPACIO_FIRMA);
            spacing.setAfter(java.math.BigInteger.ZERO);
            return true;
        }

        if (indiceParrafoPrincipal == 0) {
            spacing.setBefore(ESPACIO_UNO);
            spacing.setAfter(ESPACIO_UNO);
            return true;
        }

        if (indiceParrafoPrincipal == 1) {
            spacing.setAfter(ESPACIO_UNO);
            return true;
        }

        if (indiceParrafoPrincipal >= 2 && indiceParrafoPrincipal <= 4) {
            spacing.setAfter(java.math.BigInteger.ZERO);
            return true;
        }

        return true;
    }

    private void reemplazarMarcadoresEnDocumento(XWPFDocument document, Tramite tramite, String observacion) {
        List<MarcadorRegex> marcadores = construirMarcadoresRegex(tramite, observacion);

        for (XWPFParagraph paragraph : document.getParagraphs()) {
            reemplazarMarcadoresParrafo(paragraph, marcadores);
        }

        for (XWPFTable table : document.getTables()) {
            reemplazarMarcadoresTabla(table, marcadores);
        }

        for (XWPFHeader header : document.getHeaderList()) {
            for (XWPFParagraph paragraph : header.getParagraphs()) {
                reemplazarMarcadoresParrafo(paragraph, marcadores);
            }
            for (XWPFTable table : header.getTables()) {
                reemplazarMarcadoresTabla(table, marcadores);
            }
        }

        for (XWPFFooter footer : document.getFooterList()) {
            for (XWPFParagraph paragraph : footer.getParagraphs()) {
                reemplazarMarcadoresParrafo(paragraph, marcadores);
            }
            for (XWPFTable table : footer.getTables()) {
                reemplazarMarcadoresTabla(table, marcadores);
            }
        }
    }

    private void reemplazarMarcadoresTabla(XWPFTable table, List<MarcadorRegex> marcadores) {
        for (XWPFTableRow row : table.getRows()) {
            for (XWPFTableCell cell : row.getTableCells()) {
                for (XWPFParagraph paragraph : cell.getParagraphs()) {
                    reemplazarMarcadoresParrafo(paragraph, marcadores);
                }
                for (XWPFTable nested : cell.getTables()) {
                    reemplazarMarcadoresTabla(nested, marcadores);
                }
            }
        }
    }

    private void reemplazarMarcadoresParrafo(XWPFParagraph paragraph, List<MarcadorRegex> marcadores) {
        if (paragraph == null || paragraph.getRuns() == null || paragraph.getRuns().isEmpty()) {
            return;
        }

        for (MarcadorRegex marcador : marcadores) {
            reemplazarMarcadorRegexEnParrafo(paragraph, marcador);
        }
    }

    private void reemplazarMarcadorRegexEnParrafo(XWPFParagraph paragraph, MarcadorRegex marcador) {
        if (paragraph == null || marcador == null) {
            return;
        }

        Pattern pattern = Pattern.compile(marcador.regex(), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        int guard = 0;

        while (true) {
            guard++;
            if (guard > 256) {
                logger.warn("Se alcanzó el límite de reemplazos para marcador regex en un párrafo: {}", marcador.regex());
                return;
            }

            List<XWPFRun> runs = paragraph.getRuns();
            if (runs == null || runs.isEmpty()) {
                return;
            }

            String textoParrafo = concatenarTextoRuns(runs);
            if (textoParrafo.isEmpty()) {
                return;
            }

            Matcher matcher = pattern.matcher(textoParrafo);
            if (!matcher.find()) {
                return;
            }

            if (matcher.group().equals(marcador.valor())) {
                return;
            }

            aplicarReemplazoEnRangoDeRuns(paragraph, runs, matcher.start(), matcher.end(), marcador.valor());
        }
    }

    private String concatenarTextoRuns(List<XWPFRun> runs) {
        StringBuilder texto = new StringBuilder();
        for (XWPFRun run : runs) {
            texto.append(obtenerTextoRun(run));
        }
        return texto.toString();
    }

    private void aplicarReemplazoEnRangoDeRuns(XWPFParagraph paragraph, List<XWPFRun> runs, int inicio, int fin, String reemplazo) {
        int indiceRunInicio = -1;
        int indiceRunFin = -1;
        int offsetInicio = 0;
        int offsetFin = 0;
        int cursor = 0;

        for (int i = 0; i < runs.size(); i++) {
            String textoRun = obtenerTextoRun(runs.get(i));
            int longitud = textoRun.length();
            int limite = cursor + longitud;

            if (indiceRunInicio == -1 && inicio < limite) {
                indiceRunInicio = i;
                offsetInicio = Math.max(0, inicio - cursor);
            }

            if (indiceRunInicio != -1 && fin <= limite) {
                indiceRunFin = i;
                offsetFin = Math.max(0, fin - cursor);
                break;
            }

            cursor = limite;
        }

        if (indiceRunInicio == -1 || indiceRunFin == -1) {
            return;
        }

        int indiceRunEstilo = seleccionarRunEstilo(runs, inicio, fin, indiceRunInicio);

        XWPFRun runInicio = runs.get(indiceRunInicio);
        XWPFRun runFin = runs.get(indiceRunFin);
        XWPFRun runEstilo = runs.get(indiceRunEstilo);
        String textoInicio = obtenerTextoRun(runInicio);
        String textoFin = obtenerTextoRun(runFin);

        String prefijo = textoInicio.substring(0, Math.min(offsetInicio, textoInicio.length()));
        String sufijo = textoFin.substring(Math.min(offsetFin, textoFin.length()));

        if (indiceRunInicio == indiceRunFin) {
            runInicio.setText(prefijo, 0);

            XWPFRun runReemplazo = paragraph.insertNewRun(indiceRunInicio + 1);
            copiarFormatoRun(runEstilo, runReemplazo);
            runReemplazo.setText(reemplazo, 0);

            if (!sufijo.isEmpty()) {
                XWPFRun runSufijo = paragraph.insertNewRun(indiceRunInicio + 2);
                copiarFormatoRun(runFin, runSufijo);
                runSufijo.setText(sufijo, 0);
            }
            return;
        }

        runInicio.setText(prefijo, 0);

        for (int i = indiceRunInicio + 1; i < indiceRunFin; i++) {
            runs.get(i).setText("", 0);
        }

        runFin.setText(sufijo, 0);

        XWPFRun runReemplazo = paragraph.insertNewRun(indiceRunInicio + 1);
        copiarFormatoRun(runEstilo, runReemplazo);
        runReemplazo.setText(reemplazo, 0);
    }

    private int seleccionarRunEstilo(List<XWPFRun> runs, int inicio, int fin, int indiceDefecto) {
        int mejorIndice = indiceDefecto;
        int mejorCobertura = -1;
        int cursor = 0;

        for (int i = 0; i < runs.size(); i++) {
            String textoRun = obtenerTextoRun(runs.get(i));
            int longitud = textoRun.length();
            int runInicio = cursor;
            int runFin = cursor + longitud;

            int cobertura = Math.max(0, Math.min(fin, runFin) - Math.max(inicio, runInicio));
            if (cobertura > mejorCobertura) {
                mejorCobertura = cobertura;
                mejorIndice = i;
            } else if (cobertura > 0 && cobertura == mejorCobertura) {
                boolean actualBold = runs.get(i).isBold();
                boolean mejorBold = runs.get(mejorIndice).isBold();
                if (actualBold && !mejorBold) {
                    mejorIndice = i;
                }
            }

            cursor = runFin;
        }

        return mejorIndice;
    }

    private void copiarFormatoRun(XWPFRun source, XWPFRun target) {
        if (source == null || target == null) {
            return;
        }

        // Aplicamos solo propiedades "seguras" para evitar arrastrar efectos de
        // texto avanzados del DOCX (que en algunos motores PDF pueden verse como tachado).
        target.setBold(source.isBold());
        target.setItalic(source.isItalic());
        target.setStrikeThrough(false);
        target.setUnderline(source.getUnderline());

        String color = source.getColor();
        if (color != null && !color.isBlank()) {
            target.setColor(color);
        }

        String fontFamily = source.getFontFamily();
        if (fontFamily != null && !fontFamily.isBlank()) {
            target.setFontFamily(fontFamily);
        }

        int fontSize = source.getFontSize();
        if (fontSize > 0) {
            target.setFontSize(fontSize);
        }

        // Limpieza defensiva por si el constructor del run arrastra marcadores de tachado.
        if (target.getCTR() != null && target.getCTR().isSetRPr()) {
            CTRPr rPr = target.getCTR().getRPr();
            invocarMetodoSinArgumentosSiExiste(rPr, "unsetStrike");
            invocarMetodoSinArgumentosSiExiste(rPr, "unsetDstrike");
        }
    }

    private void invocarMetodoSinArgumentosSiExiste(Object target, String methodName) {
        if (target == null || methodName == null || methodName.isBlank()) {
            return;
        }

        try {
            target.getClass().getMethod(methodName).invoke(target);
        } catch (NoSuchMethodException ignored) {
            // Algunas versiones de POI/XMLBeans no exponen estos metodos.
        } catch (Exception ex) {
            logger.debug("No fue posible invocar {} en {}: {}", methodName, target.getClass().getSimpleName(), ex.getMessage());
        }
    }

    private String obtenerTextoRun(XWPFRun run) {
        if (run == null) {
            return "";
        }
        String texto = run.getText(0);
        return texto == null ? "" : texto;
    }

    private record MarcadorRegex(String regex, String valor) {
    }

    private void insertarFirmaEnDocumento(XWPFDocument document, Tramite tramite) {
        if (recortarParrafosVaciosFirma) {
            recortarParrafosVaciosAlrededorDeFirma(
                document,
                RECORTE_PARRAFOS_VACIOS_ANTES,
                RECORTE_PARRAFOS_VACIOS_DESPUES
            );
        }

        for (XWPFParagraph paragraph : document.getParagraphs()) {
            insertarFirmaEnParrafo(paragraph, document, tramite);
        }

        for (XWPFTable table : document.getTables()) {
            insertarFirmaEnTabla(table, document, tramite);
        }

        for (XWPFHeader header : document.getHeaderList()) {
            for (XWPFParagraph paragraph : header.getParagraphs()) {
                insertarFirmaEnParrafo(paragraph, document, tramite);
            }
            for (XWPFTable table : header.getTables()) {
                insertarFirmaEnTabla(table, document, tramite);
            }
        }

        for (XWPFFooter footer : document.getFooterList()) {
            for (XWPFParagraph paragraph : footer.getParagraphs()) {
                insertarFirmaEnParrafo(paragraph, document, tramite);
            }
            for (XWPFTable table : footer.getTables()) {
                insertarFirmaEnTabla(table, document, tramite);
            }
        }
    }

    private void insertarFirmaEnTabla(XWPFTable table, XWPFDocument document, Tramite tramite) {
        for (XWPFTableRow row : table.getRows()) {
            for (XWPFTableCell cell : row.getTableCells()) {
                for (XWPFParagraph paragraph : cell.getParagraphs()) {
                    insertarFirmaEnParrafo(paragraph, document, tramite);
                }
                for (XWPFTable nested : cell.getTables()) {
                    insertarFirmaEnTabla(nested, document, tramite);
                }
            }
        }
    }

    private void insertarFirmaEnParrafo(XWPFParagraph paragraph, XWPFDocument document, Tramite tramite) {
        if (paragraph == null || paragraph.getRuns() == null || paragraph.getRuns().isEmpty()) {
            return;
        }

        XWPFRun runEstiloBase = paragraph.getRuns().get(0);

        StringBuilder textoParrafo = new StringBuilder();
        for (XWPFRun run : paragraph.getRuns()) {
            String textoRun = run.getText(0);
            textoParrafo.append(textoRun == null ? "" : textoRun);
        }

        String original = textoParrafo.toString();
        if (original.isBlank()) {
            return;
        }

        String marcador = null;
        if (original.contains(MARCADOR_FIRMA_ANGULAR)) {
            marcador = MARCADOR_FIRMA_ANGULAR;
        } else if (original.contains(MARCADOR_FIRMA_GUILLEMET)) {
            marcador = MARCADOR_FIRMA_GUILLEMET;
        }

        if (marcador == null) {
            return;
        }

        String antes = original.substring(0, original.indexOf(marcador));
        String despues = original.substring(original.indexOf(marcador) + marcador.length());

        if (compactarLayoutFirma) {
            antes = reducirEspacioPrevioFirma(antes, RECORTE_FIRMA_ANTES);
            despues = reducirEspacioPosteriorFirma(despues, RECORTE_FIRMA_DESPUES);
        }

        while (!paragraph.getRuns().isEmpty()) {
            paragraph.removeRun(0);
        }

        if (compactarLayoutFirma) {
            compactarParrafoFirma(paragraph);
        }

        if (!antes.isBlank()) {
            XWPFRun runAntes = paragraph.createRun();
            copiarFormatoRun(runEstiloBase, runAntes);
            runAntes.setText(antes);
        }

        try {
            Resource firma = resourceLoader.getResource("classpath:templates/firma.jpeg");
            try (InputStream firmaInputStream = firma.getInputStream()) {
                XWPFRun runFirma = paragraph.createRun();
                runFirma.addPicture(
                        firmaInputStream,
                        XWPFDocument.PICTURE_TYPE_JPEG,
                        "firma.jpeg",
                        Units.toEMU(firmaAnchoPx),
                        Units.toEMU(firmaAltoPx)
                );
            }
        } catch (Exception ex) {
            logger.warn("No fue posible insertar la firma desde templates/firma.jpeg: {}", ex.getMessage());
            XWPFRun runFallback = paragraph.createRun();
            copiarFormatoRun(runEstiloBase, runFallback);
            runFallback.setText("[firma no disponible]");
        }

        if (!despues.isBlank()) {
            XWPFRun runDespues = paragraph.createRun();
            copiarFormatoRun(runEstiloBase, runDespues);
            runDespues.setText(despues);
        }

        if (incluirDetalleFirmaEnPdf) {
            XWPFRun runDetalle = paragraph.createRun();
            runDetalle.setFontSize(7);
            runDetalle.setText(" " + generarLeyendaFirmaDigital(tramite)
                    + " | Hash de verificación: " + generarHashVisibleFirma(tramite));
        }
    }

    private String reducirEspacioPrevioFirma(String textoAntesFirma, int cantidad) {
        if (textoAntesFirma == null || textoAntesFirma.isEmpty()) {
            return textoAntesFirma;
        }

        String ajustado = textoAntesFirma;
        int saltosRecortados = 0;

        // Quita hasta N saltos en blanco justo antes de la firma.
        while (saltosRecortados < cantidad) {
            String candidato = ajustado.replaceFirst("[\\t ]*\\r?\\n[\\t ]*$", "");
            if (candidato.equals(ajustado)) {
                break;
            }
            ajustado = candidato;
            saltosRecortados++;
        }

        if (saltosRecortados < cantidad) {
            int espaciosExtra = cantidad - saltosRecortados;
            ajustado = ajustado.replaceFirst("[\\t ]{0," + espaciosExtra + "}$", "");
        }

        return ajustado;
    }

    private String reducirEspacioPosteriorFirma(String textoDespuesFirma, int cantidad) {
        if (textoDespuesFirma == null || textoDespuesFirma.isEmpty()) {
            return textoDespuesFirma;
        }

        String ajustado = textoDespuesFirma;
        int saltosRecortados = 0;

        // Quita hasta N saltos en blanco justo después de la firma.
        while (saltosRecortados < cantidad) {
            String candidato = ajustado.replaceFirst("^[\\t ]*\\r?\\n[\\t ]*", "");
            if (candidato.equals(ajustado)) {
                break;
            }
            ajustado = candidato;
            saltosRecortados++;
        }

        if (saltosRecortados < cantidad) {
            int espaciosExtra = cantidad - saltosRecortados;
            ajustado = ajustado.replaceFirst("^[\\t ]{0," + espaciosExtra + "}", "");
        }

        return ajustado;
    }

    private void compactarParrafoFirma(XWPFParagraph paragraph) {
        if (paragraph == null || paragraph.getCTP() == null) {
            return;
        }

        paragraph.setPageBreak(false);

        var pPr = paragraph.getCTP().isSetPPr() ? paragraph.getCTP().getPPr() : paragraph.getCTP().addNewPPr();
        CTSpacing spacing = pPr.isSetSpacing() ? pPr.getSpacing() : pPr.addNewSpacing();
        spacing.setBefore(BigInteger.ZERO);
        spacing.setAfter(BigInteger.ZERO);
    }

    private void recortarParrafosVaciosAlrededorDeFirma(XWPFDocument document, int cantidadAntes, int cantidadDespues) {
        if (document == null || (cantidadAntes <= 0 && cantidadDespues <= 0)) {
            return;
        }

        int i = 0;
        while (i < document.getBodyElements().size()) {
            IBodyElement element = document.getBodyElements().get(i);
            if (!(element instanceof XWPFParagraph paragraph) || !contieneMarcadorFirma(paragraph.getText())) {
                i++;
                continue;
            }

            int pendientes = cantidadAntes;
            int j = i - 1;
            while (j >= 0 && pendientes > 0) {
                IBodyElement previo = document.getBodyElements().get(j);
                if (!(previo instanceof XWPFParagraph previoParrafo)) {
                    break;
                }

                String textoPrevio = previoParrafo.getText() == null ? "" : previoParrafo.getText().trim();
                if (!textoPrevio.isEmpty()) {
                    break;
                }

                document.removeBodyElement(j);
                i--;
                j--;
                pendientes--;
            }

            int pendientesDespues = cantidadDespues;
            int k = i + 1;
            while (k < document.getBodyElements().size() && pendientesDespues > 0) {
                IBodyElement siguiente = document.getBodyElements().get(k);
                if (!(siguiente instanceof XWPFParagraph siguienteParrafo)) {
                    break;
                }

                String textoSiguiente = siguienteParrafo.getText() == null ? "" : siguienteParrafo.getText().trim();
                if (!textoSiguiente.isEmpty()) {
                    break;
                }

                document.removeBodyElement(k);
                pendientesDespues--;
            }

            i++;
        }
    }

    private boolean contieneMarcadorFirma(String texto) {
        if (texto == null || texto.isBlank()) {
            return false;
        }
        return texto.contains(MARCADOR_FIRMA_ANGULAR) || texto.contains(MARCADOR_FIRMA_GUILLEMET);
    }

    private String generarLeyendaFirmaDigital(Tramite tramite) {
        if (tramite == null) {
            return "Documento firmado digitalmente por la Alcaldía Municipal de Cabuyaro.";
        }

        String firmante = nombreAlcaldePlantilla(tramite);
        if (firmante == null || firmante.isBlank()) {
            return "Documento firmado digitalmente por la Alcaldía Municipal de Cabuyaro.";
        }

        return "Documento firmado digitalmente por " + firmante + ".";
    }

    private String generarHashVisibleFirma(Tramite tramite) {
        if (tramite == null) {
            return "NO-DISPONIBLE";
        }

        String base = String.join("|",
                valor(tramite.getNumeroRadicado()),
                valor(tramite.getNumeroDocumento()),
                valor(tramite.getCodigoVerificacion()),
                valor(tramite.getTipo_certificado()),
                tramite.getFechaFirmaAlcalde() != null ? tramite.getFechaFirmaAlcalde().toString() : ""
        );

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(base.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte b : hash) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString().toUpperCase(LOCALE_ES);
        } catch (NoSuchAlgorithmException e) {
            logger.warn("No fue posible generar hash visible de firma digital: {}", e.getMessage());
            return "NO-DISPONIBLE";
        }
    }

    private void normalizarIdsInternosParaPdf(XWPFDocument document) {
        Set<String> bookmarksUsados = new HashSet<>();

        for (XWPFParagraph paragraph : document.getParagraphs()) {
            normalizarParrafoParaPdf(paragraph, bookmarksUsados);
        }

        for (XWPFTable table : document.getTables()) {
            normalizarTablaParaPdf(table, bookmarksUsados);
        }

        for (XWPFHeader header : document.getHeaderList()) {
            for (XWPFParagraph paragraph : header.getParagraphs()) {
                normalizarParrafoParaPdf(paragraph, bookmarksUsados);
            }
            for (XWPFTable table : header.getTables()) {
                normalizarTablaParaPdf(table, bookmarksUsados);
            }
        }

        for (XWPFFooter footer : document.getFooterList()) {
            for (XWPFParagraph paragraph : footer.getParagraphs()) {
                normalizarParrafoParaPdf(paragraph, bookmarksUsados);
            }
            for (XWPFTable table : footer.getTables()) {
                normalizarTablaParaPdf(table, bookmarksUsados);
            }
        }
    }

    private void normalizarTablaParaPdf(XWPFTable table, Set<String> bookmarksUsados) {
        for (XWPFTableRow row : table.getRows()) {
            for (XWPFTableCell cell : row.getTableCells()) {
                for (XWPFParagraph paragraph : cell.getParagraphs()) {
                    normalizarParrafoParaPdf(paragraph, bookmarksUsados);
                }
                for (XWPFTable nested : cell.getTables()) {
                    normalizarTablaParaPdf(nested, bookmarksUsados);
                }
            }
        }
    }

    private void normalizarParrafoParaPdf(XWPFParagraph paragraph, Set<String> bookmarksUsados) {
        if (paragraph == null || paragraph.getCTP() == null) {
            return;
        }

        for (CTBookmark bookmark : paragraph.getCTP().getBookmarkStartList()) {
            String nombre = bookmark.getName();
            if (nombre == null || nombre.isBlank()) {
                continue;
            }

            String nombreNormalizado = nombre;
            int secuencia = 1;
            while (bookmarksUsados.contains(nombreNormalizado)) {
                nombreNormalizado = nombre + "_" + secuencia;
                secuencia++;
            }
            bookmark.setName(nombreNormalizado);
            bookmarksUsados.add(nombreNormalizado);
        }

        for (CTHyperlink hyperlink : paragraph.getCTP().getHyperlinkList()) {
            if (hyperlink.isSetAnchor()) {
                hyperlink.unsetAnchor();
            }
        }
    }

    private PdfGeneracionResultado convertirDocxConPlantillaAPdf(XWPFDocument wordDoc, String tipo) {
        try {
            ByteArrayOutputStream docxBytes = new ByteArrayOutputStream();
            wordDoc.write(docxBytes);

            byte[] docxContenido = docxBytes.toByteArray();
            Exception ultimoError = null;

            if (usarSpire) {
                try {
                    byte[] pdfSpire = ejecutarEtapaPdfConMetricas(
                            "convert",
                            "spire.doc",
                            tipo,
                            () -> convertirDocxConSpire(docxContenido)
                    );
                    return new PdfGeneracionResultado(pdfSpire, "spire.doc");
                } catch (Exception ex) {
                    ultimoError = ex;
                    logger.warn("Spire.Doc no pudo convertir DOCX a PDF. Se usará motor de respaldo habilitado: {}", ex.getMessage());
                }
            }

            if (usarGotenberg) {
                if (!gotenbergDisponible()) {
                    String mensaje = "Gotenberg está habilitado pero app.pdf.gotenberg.url no está configurado";
                    if (!gotenbergFallbackHabilitado) {
                        throw new IllegalStateException(mensaje);
                    }
                    logger.warn("{}. Se usará motor local como respaldo.", mensaje);
                } else {
                    try {
                        byte[] pdfGotenberg = ejecutarEtapaPdfConMetricas(
                                "convert",
                                "gotenberg",
                                tipo,
                                () -> convertirDocxConGotenberg(docxContenido)
                        );
                        return new PdfGeneracionResultado(pdfGotenberg, "gotenberg");
                    } catch (Exception ex) {
                        ultimoError = ex;
                        if (!gotenbergFallbackHabilitado) {
                            throw ex;
                        }
                        logger.warn("Gotenberg no pudo convertir DOCX a PDF. Se usará motor local como respaldo: {}", ex.getMessage());
                    }
                }
            }

            if (usarLibreOffice) {
                try {
                    byte[] pdfLibreOffice = ejecutarEtapaPdfConMetricas(
                            "convert",
                            "libreoffice",
                            tipo,
                            () -> convertirDocxConLibreOffice(docxContenido)
                    );
                    if (pdfLibreOffice != null && pdfLibreOffice.length > 0) {
                        return new PdfGeneracionResultado(pdfLibreOffice, "libreoffice");
                    }
                } catch (Exception ex) {
                    ultimoError = ex;
                    logger.warn("LibreOffice está habilitado pero no pudo convertir; se usará docx4j como respaldo: {}", ex.getMessage());
                }
            }

            if (usarDocx4j) {
                try {
                    byte[] pdfDocx4j = ejecutarEtapaPdfConMetricas(
                            "convert",
                            "docx4j",
                            tipo,
                            () -> convertirDocxConDocx4j(docxContenido)
                    );
                    return new PdfGeneracionResultado(pdfDocx4j, "docx4j");
                } catch (Exception ex) {
                    if (ultimoError != null && ultimoError != ex) {
                        ex.addSuppressed(ultimoError);
                    }
                    throw ex;
                }
            }

            if (ultimoError != null) {
                throw new IllegalStateException(
                        "No fue posible convertir DOCX a PDF con los motores habilitados (docx4j desactivado)",
                        ultimoError
                );
            }

            throw new IllegalStateException(
                    "No hay motores de conversion DOCX->PDF habilitados. Configure Spire/Gotenberg o habilite app.pdf.use-docx4j/app.pdf.use-libreoffice"
            );
        } catch (IOException e) {
            throw new IllegalStateException("No fue posible convertir la plantilla DOCX a PDF", e);
        }
    }

    private record PdfGeneracionResultado(byte[] contenidoPdf, String engine) {
    }

    private byte[] convertirDocxConSpire(byte[] docxContenido) {
        try {
            com.spire.doc.Document document = new com.spire.doc.Document();
            document.loadFromStream(new ByteArrayInputStream(docxContenido), com.spire.doc.FileFormat.Docx);

            ByteArrayOutputStream pdfOutput = new ByteArrayOutputStream();
            document.saveToStream(pdfOutput, com.spire.doc.FileFormat.PDF);
            return pdfOutput.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException("No fue posible convertir la plantilla DOCX a PDF con Spire.Doc", ex);
        }
    }

    private byte[] convertirDocxConDocx4j(byte[] docxContenido) {
        try {
            WordprocessingMLPackage wordMLPackage = WordprocessingMLPackage.load(
                    new ByteArrayInputStream(docxContenido)
            );

            PhysicalFont fuenteMavenPro = registrarFuenteMavenProSiDisponible();
            PhysicalFont fuenteMavenProBold = resolverFuenteMavenProBold();
            if (fuenteMavenPro != null && fuenteMavenProBold != null) {
                Mapper fontMapper = new IdentityPlusMapper();
                mapearFuenteMavenPro(fontMapper, fuenteMavenPro, fuenteMavenProBold);
                wordMLPackage.setFontMapper(fontMapper);
            } else {
                logger.info("Se conserva el mapeo de fuentes por defecto para mantener negritas de plantilla.");
            }

            PdfSettings pdfSettings = new PdfSettings();
            PdfConversion conversion = new Conversion(wordMLPackage);

            ByteArrayOutputStream pdfOutput = new ByteArrayOutputStream();
            conversion.output(pdfOutput, pdfSettings);
            return pdfOutput.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("No fue posible convertir la plantilla DOCX a PDF con docx4j", e);
        }
    }

    private PhysicalFont registrarFuenteMavenProSiDisponible() {
        PhysicalFont cache = fuenteMavenProCache;
        if (cache != null) {
            return cache;
        }

        synchronized (this) {
            if (fuenteMavenProCache != null) {
                return fuenteMavenProCache;
            }

            PhysicalFont existente = PhysicalFonts.get(FUENTE_MAVEN_PRO);
            if (existente != null) {
                fuenteMavenProCache = existente;
                fuenteMavenProBoldCache = buscarFuenteMavenProEnCatalogo(true);
                return fuenteMavenProCache;
            }

            Resource fuente = resourceLoader.getResource(RECURSO_MAVEN_PRO_TTF);
            if (!fuente.exists()) {
                logger.warn("No se encontró {}. El PDF usará la fuente disponible del sistema.", RECURSO_MAVEN_PRO_TTF);
                return null;
            }

            Path temporal = null;
            try (InputStream input = fuente.getInputStream()) {
                temporal = Files.createTempFile("maven-pro-", ".ttf");
                Files.copy(input, temporal, StandardCopyOption.REPLACE_EXISTING);
                temporal.toFile().deleteOnExit();

                PhysicalFonts.addPhysicalFonts(FUENTE_MAVEN_PRO, temporal.toUri());
                fuenteMavenProCache = buscarFuenteMavenProEnCatalogo(false);
                fuenteMavenProBoldCache = buscarFuenteMavenProEnCatalogo(true);
                if (fuenteMavenProCache == null) {
                    logger.warn("Se cargó {} pero docx4j no registró '{}' por nombre.", RECURSO_MAVEN_PRO_TTF, FUENTE_MAVEN_PRO);
                } else {
                    logger.info("Fuente '{}' registrada para conversión PDF desde {}", FUENTE_MAVEN_PRO, RECURSO_MAVEN_PRO_TTF);
                    if (fuenteMavenProBoldCache != null) {
                        logger.info("Variante en negrita de '{}' detectada en catálogo de docx4j", FUENTE_MAVEN_PRO);
                    } else {
                        logger.warn("No se detectó variante bold explícita de '{}'; no se forzará mapeo de fuente para preservar negritas", FUENTE_MAVEN_PRO);
                    }
                }
                return fuenteMavenProCache;
            } catch (Exception ex) {
                logger.warn("No fue posible registrar fuente Maven Pro para PDF: {}", ex.getMessage());
                if (temporal != null) {
                    try {
                        Files.deleteIfExists(temporal);
                    } catch (IOException ignored) {
                    }
                }
                return null;
            }
        }
    }

    private void mapearFuenteMavenPro(Mapper fontMapper, PhysicalFont regular, PhysicalFont bold) {
        if (fontMapper == null || regular == null) {
            return;
        }

        String[] aliases = new String[]{FUENTE_MAVEN_PRO, FUENTE_MAVEN_PRO_ALIAS};

        for (String alias : aliases) {
            fontMapper.put(alias, regular);
            fontMapper.registerRegularForm(alias, regular);
            fontMapper.registerItalicForm(alias, regular);
            if (bold != null) {
                fontMapper.registerBoldForm(alias, bold);
                fontMapper.registerBoldItalicForm(alias, bold);
            }
        }
    }

    private PhysicalFont resolverFuenteMavenProBold() {
        if (fuenteMavenProBoldCache != null) {
            return fuenteMavenProBoldCache;
        }

        synchronized (this) {
            if (fuenteMavenProBoldCache != null) {
                return fuenteMavenProBoldCache;
            }
            fuenteMavenProBoldCache = buscarFuenteMavenProEnCatalogo(true);
            return fuenteMavenProBoldCache;
        }
    }

    private PhysicalFont buscarFuenteMavenProEnCatalogo(boolean bold) {
        PhysicalFont firstMaven = null;
        for (Map.Entry<String, PhysicalFont> entry : PhysicalFonts.getPhysicalFonts().entrySet()) {
            String nombreFuente = entry.getKey();
            if (nombreFuente == null) {
                continue;
            }

            String lower = nombreFuente.toLowerCase(LOCALE_ES);
            if (!lower.contains("maven")) {
                continue;
            }

            if (firstMaven == null) {
                firstMaven = entry.getValue();
            }

            boolean esBold = lower.contains("bold") || lower.contains("black") || lower.contains("extrabold");
            if (bold && esBold) {
                return entry.getValue();
            }

            if (!bold && !esBold) {
                return entry.getValue();
            }
        }

        // Si se pidió variante en negrita y no se encontró una real, retornamos null para
        // evitar mapear "bold" a la misma variante regular.
        if (bold) {
            return null;
        }

        return firstMaven;
    }

    private byte[] convertirDocxConLibreOffice(byte[] docxContenido) {
        String ejecutable = resolverEjecutableSoffice();
        if (ejecutable == null || ejecutable.isBlank()) {
            return null;
        }

        Path dirTemporal = null;
        try {
            dirTemporal = Files.createTempDirectory("tramites-pdf-");
            Path docxPath = dirTemporal.resolve("certificado.docx");
            Path pdfPath = dirTemporal.resolve("certificado.pdf");
            Files.write(docxPath, docxContenido);

            ProcessBuilder pb = new ProcessBuilder(
                    ejecutable,
                    "--headless",
                    "--convert-to", "pdf:writer_pdf_Export",
                    "--outdir", dirTemporal.toString(),
                    docxPath.toString()
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();

            boolean termino = process.waitFor(60, TimeUnit.SECONDS);
            String salida = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

            if (!termino) {
                process.destroyForcibly();
                logger.warn("LibreOffice no terminó a tiempo al convertir DOCX a PDF");
                return null;
            }

            if (process.exitValue() != 0) {
                logger.warn("LibreOffice devolvió código {} al convertir DOCX a PDF. Salida: {}", process.exitValue(), salida);
                return null;
            }

            if (!Files.exists(pdfPath)) {
                logger.warn("LibreOffice no generó el PDF esperado en {}", pdfPath);
                return null;
            }

            return Files.readAllBytes(pdfPath);
        } catch (Exception ex) {
            logger.warn("No fue posible convertir DOCX a PDF con LibreOffice: {}", ex.getMessage());
            return null;
        } finally {
            eliminarDirectorioTemporal(dirTemporal);
        }
    }

    private boolean gotenbergDisponible() {
        return gotenbergUrl != null && !gotenbergUrl.isBlank();
    }

    private byte[] convertirDocxConGotenberg(byte[] docxContenido) {
        if (!gotenbergDisponible()) {
            throw new IllegalStateException("URL de Gotenberg no configurada");
        }

        String boundary = "----tramitesBoundary" + System.nanoTime();

        try {
            byte[] payload = construirPayloadMultipartGotenberg(boundary, docxContenido);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(gotenbergUrl))
                    .timeout(Duration.ofSeconds(Math.max(3, gotenbergTimeoutSegundos)))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(payload))
                    .build();

            HttpResponse<byte[]> response = gotenbergHttpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            int status = response.statusCode();
            if (status < 200 || status >= 300) {
                String detalle = response.body() == null
                        ? "sin detalle"
                        : new String(response.body(), StandardCharsets.UTF_8);
                throw new IllegalStateException("Gotenberg devolvió HTTP " + status + ": " + detalle);
            }

            byte[] pdf = response.body();
            if (pdf == null || pdf.length == 0) {
                throw new IllegalStateException("Gotenberg devolvió un PDF vacío");
            }
            return pdf;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Conversión con Gotenberg interrumpida", ex);
        } catch (IOException ex) {
            throw new IllegalStateException("No fue posible convertir DOCX a PDF con Gotenberg", ex);
        }
    }

    private byte[] construirPayloadMultipartGotenberg(String boundary, byte[] docxContenido) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        String separador = "\r\n";

        output.write(("--" + boundary + separador).getBytes(StandardCharsets.UTF_8));
        output.write(("Content-Disposition: form-data; name=\"files\"; filename=\"certificado.docx\"" + separador)
                .getBytes(StandardCharsets.UTF_8));
        output.write(("Content-Type: application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                + separador + separador).getBytes(StandardCharsets.UTF_8));
        output.write(docxContenido);
        output.write(separador.getBytes(StandardCharsets.UTF_8));

        output.write(("--" + boundary + "--" + separador).getBytes(StandardCharsets.UTF_8));
        return output.toByteArray();
    }

    private String resolverEjecutableSoffice() {
        String desdeEnv = System.getenv("SOFFICE_PATH");
        if (desdeEnv != null && !desdeEnv.isBlank() && Files.exists(Path.of(desdeEnv))) {
            return desdeEnv;
        }

        String[] candidatos = new String[] {
                "C:/Program Files/LibreOffice/program/soffice.exe",
                "C:/Program Files (x86)/LibreOffice/program/soffice.exe",
                "soffice"
        };

        for (String candidato : candidatos) {
            try {
                if ("soffice".equals(candidato) || Files.exists(Path.of(candidato))) {
                    return candidato;
                }
            } catch (Exception ignored) {
            }
        }

        return null;
    }

    private void eliminarDirectorioTemporal(Path directorio) {
        if (directorio == null || !Files.exists(directorio)) {
            return;
        }

        try (var stream = Files.walk(directorio)) {
            stream.sorted(java.util.Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ignored) {
                        }
                    });
        } catch (IOException ignored) {
        }
    }

    private String generarNombrePdf(Tramite tramite, boolean aprobado) {
        String nombreSolicitante = valorMayusculas(tramite.getNombreSolicitante());
        String nombreNormalizado = Normalizer.normalize(nombreSolicitante, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^A-Z0-9]", "");

        if (nombreNormalizado.isBlank()) {
            nombreNormalizado = "SOLICITANTE";
        }

        return "CERTIFICADORESIDENCIA_" + nombreNormalizado + ".PDF";
    }

    private String nombreVerificadorPlantilla(Tramite tramite) {
        if (tramite.getUsuarioVerificador() != null
                && tramite.getUsuarioVerificador().getNombreCompleto() != null
                && !tramite.getUsuarioVerificador().getNombreCompleto().isBlank()) {
            return valorMayusculas(tramite.getUsuarioVerificador().getNombreCompleto());
        }
        return "VERIFICADOR MUNICIPAL";
    }

    private String nombreAlcaldePlantilla(Tramite tramite) {
        if (tramite.getUsuarioAlcalde() != null
                && tramite.getUsuarioAlcalde().getNombreCompleto() != null
                && !tramite.getUsuarioAlcalde().getNombreCompleto().isBlank()) {
            return valorMayusculas(tramite.getUsuarioAlcalde().getNombreCompleto());
        }
        return "ALCALDE MUNICIPAL";
    }

    private String reemplazarRegex(String texto, String regex, String reemplazo) {
        return texto.replaceAll("(?i)" + regex, java.util.regex.Matcher.quoteReplacement(reemplazo));
    }

    private String valor(String texto) {
        return texto == null ? "" : texto.trim();
    }

    private String valorMayusculas(String texto) {
        return valor(texto).toUpperCase(LOCALE_ES);
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private String obtenerFuenteSegunTipo(Tramite tramite) {
        String tipo = tramite.getTipo_certificado() == null ? "" : tramite.getTipo_certificado().toLowerCase();
        if (tipo.contains("sisben")) return "SISBEN";
        if (tipo.contains("electoral")) return "REGISTRADURÍA NACIONAL";
        return "JUNTA DE ACCIÓN COMUNAL";
    }

    private String capitalizar(String texto) {
        if (texto == null || texto.isBlank()) {
            return "";
        }
        return texto.substring(0, 1).toUpperCase(LOCALE_ES) + texto.substring(1);
    }

    private String numeroALetras(int numero) {
        if (numero == 0) {
            return "cero";
        }
        if (numero < 0) {
            return "menos " + numeroALetras(-numero);
        }

        if (numero < 30) {
            String[] unidades = {
                    "", "uno", "dos", "tres", "cuatro", "cinco", "seis", "siete", "ocho", "nueve",
                    "diez", "once", "doce", "trece", "catorce", "quince", "dieciseis", "diecisiete", "dieciocho", "diecinueve",
                    "veinte", "veintiuno", "veintidos", "veintitres", "veinticuatro", "veinticinco", "veintiseis", "veintisiete", "veintiocho", "veintinueve"
            };
            return unidades[numero];
        }

        if (numero < 100) {
            String[] decenas = {
                    "", "", "", "treinta", "cuarenta", "cincuenta", "sesenta", "setenta", "ochenta", "noventa"
            };
            int d = numero / 10;
            int u = numero % 10;
            return u == 0 ? decenas[d] : decenas[d] + " y " + numeroALetras(u);
        }

        if (numero < 1000) {
            if (numero == 100) {
                return "cien";
            }
            int c = numero / 100;
            int resto = numero % 100;
            String[] centenas = {
                    "", "ciento", "doscientos", "trescientos", "cuatrocientos", "quinientos",
                    "seiscientos", "setecientos", "ochocientos", "novecientos"
            };
            return resto == 0 ? centenas[c] : centenas[c] + " " + numeroALetras(resto);
        }

        if (numero < 1000000) {
            int miles = numero / 1000;
            int resto = numero % 1000;
            String baseMiles = miles == 1 ? "mil" : numeroALetras(miles) + " mil";
            return resto == 0 ? baseMiles : baseMiles + " " + numeroALetras(resto);
        }

        return String.valueOf(numero);
    }
}
