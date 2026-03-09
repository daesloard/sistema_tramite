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
    private volatile CertBundle certBundleCache;

    static {
        if (Security.getProvider("BC") == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    public DocumentoGeneradoService(ResourceLoader resourceLoader,
                                    MeterRegistry meterRegistry,
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
        try (InputStream inputStream = resource.getInputStream()) {
            com.spire.doc.Document document = new com.spire.doc.Document();
            document.loadFromStream(inputStream, com.spire.doc.FileFormat.Docx);
            return document.getText();
        } catch (Exception e) {
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
                new MarcadorRegex("«\\s*nombre\\s*s\\s*olicitante\\s*»|«\\s*nombre_colicitante\\s*»|<<\\s*nombreSolicitante\\s*>>|«\\s*nombreSolicitante\\s*»", valorMayusculas(tramite.getNombreSolicitante())),
                new MarcadorRegex("«\\s*numero\\s*d\\s*ocumento\\s*»|«\\s*numero_documento\\s*»|<<\\s*numeroDocumento\\s*>>|«\\s*numeroDocumento\\s*»", valor(tramite.getNumeroDocumento())),
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

        try (InputStream inputStream = resource.getInputStream()) {
            com.spire.doc.Document document = new com.spire.doc.Document();
            document.loadFromStream(inputStream, com.spire.doc.FileFormat.Docx);

            reemplazarMarcadoresEnSpireDoc(document, tramite, observacion);
            insertarFirmaEnSpireDoc(document, tramite);
            aplicarFuenteMavenProSpireDoc(document);

            ByteArrayOutputStream pdfOutput = new ByteArrayOutputStream();
            document.saveToStream(pdfOutput, com.spire.doc.FileFormat.PDF);
            
            return new PdfGeneracionResultado(pdfOutput.toByteArray(), "spire.doc");
        } catch (Exception e) {
            throw new IllegalStateException("No fue posible generar PDF desde plantilla DOCX usando Spire.Doc", e);
        }
    }

    private void aplicarFuenteMavenProSpireDoc(com.spire.doc.Document document) {
        for (Object sectionObj : document.getSections()) {
            com.spire.doc.Section section = (com.spire.doc.Section) sectionObj;
            for (Object paramObj : section.getParagraphs()) {
                com.spire.doc.documents.Paragraph paragraph = (com.spire.doc.documents.Paragraph) paramObj;
                paragraph.getBreakCharacterFormat().setFontName("Maven Pro");
                for (Object itemObj : paragraph.getChildObjects()) {
                    com.spire.doc.DocumentObject docObj = (com.spire.doc.DocumentObject) itemObj;
                    if (docObj instanceof com.spire.doc.fields.TextRange) {
                        com.spire.doc.fields.TextRange range = (com.spire.doc.fields.TextRange) docObj;
                        range.getCharacterFormat().setFontName("Maven Pro");
                    }
                }
            }
            
            for (Object tableObj : section.getTables()) {
                com.spire.doc.Table table = (com.spire.doc.Table) tableObj;
                for (Object rowObj : table.getRows()) {
                    com.spire.doc.TableRow row = (com.spire.doc.TableRow) rowObj;
                    for (Object cellObj : row.getCells()) {
                        com.spire.doc.TableCell cell = (com.spire.doc.TableCell) cellObj;
                        for (Object cellParaObj : cell.getParagraphs()) {
                            com.spire.doc.documents.Paragraph cellPara = (com.spire.doc.documents.Paragraph) cellParaObj;
                            cellPara.getBreakCharacterFormat().setFontName("Maven Pro");
                            for (Object itemObj : cellPara.getChildObjects()) {
                                com.spire.doc.DocumentObject docObj = (com.spire.doc.DocumentObject) itemObj;
                                if (docObj instanceof com.spire.doc.fields.TextRange) {
                                    com.spire.doc.fields.TextRange range = (com.spire.doc.fields.TextRange) docObj;
                                    range.getCharacterFormat().setFontName("Maven Pro");
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private void reemplazarMarcadoresEnSpireDoc(com.spire.doc.Document document, Tramite tramite, String observacion) {
        List<MarcadorRegex> marcadores = construirMarcadoresRegex(tramite, observacion);
        for (MarcadorRegex marcador : marcadores) {
            try {
                java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(marcador.regex(), java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.UNICODE_CASE);
                document.replace(pattern, marcador.valor() == null ? "" : marcador.valor());
            } catch (Exception ignored) {
                // Ignore if pattern cannot be applied
            }
        }
    }

    private void insertarFirmaEnSpireDoc(com.spire.doc.Document document, Tramite tramite) {
        com.spire.doc.documents.TextSelection selection = document.findString("«firma.jpeg»", false, true);
        if (selection == null) {
            selection = document.findString("<<firma.jpeg>>", false, true);
        }
        
        if (selection != null) {
            com.spire.doc.fields.TextRange range = selection.getAsOneRange();
            com.spire.doc.documents.Paragraph paragraph = range.getOwnerParagraph();
            int index = paragraph.getChildObjects().indexOf(range);
            
            Resource firmaRes = resourceLoader.getResource("classpath:templates/firma.jpeg");
            if (firmaRes.exists()) {
                try (InputStream is = firmaRes.getInputStream()) {
                    com.spire.doc.fields.DocPicture picture = new com.spire.doc.fields.DocPicture(document);
                    picture.loadImage(is);
                    picture.setWidth(170f);
                    picture.setHeight(52f);
                    paragraph.getChildObjects().insert(index, picture);
                } catch(Exception e) {
                    // Fallback if image fails
                }
            }
            
            paragraph.getChildObjects().remove(range);
            
            if (incluirDetalleFirmaEnPdf) {
                com.spire.doc.fields.TextRange detalle = paragraph.appendText(" " + generarLeyendaFirmaDigital(tramite) + " | Hash: " + generarHashVisibleFirma(tramite));
                detalle.getCharacterFormat().setFontSize(7f);
            }
        }
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
            return "NO-DISPONIBLE";
        }
    }

    private record PdfGeneracionResultado(byte[] contenidoPdf, String engine) {}

    private record MarcadorRegex(String regex, String valor) {}

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
