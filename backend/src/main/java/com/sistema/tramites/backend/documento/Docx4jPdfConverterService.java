package com.sistema.tramites.backend.documento;

import org.docx4j.Docx4J;
import org.docx4j.convert.out.FOSettings;
import org.docx4j.fonts.fop.util.FopConfigUtil;
import org.docx4j.fonts.IdentityPlusMapper;
import org.docx4j.fonts.Mapper;
import org.docx4j.fonts.PhysicalFont;
import org.docx4j.fonts.PhysicalFonts;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

@Service
public class Docx4jPdfConverterService {

    private static final Logger log = LoggerFactory.getLogger(Docx4jPdfConverterService.class);
    private static final String MAVEN_PRO_RESOURCE_PATH = "fonts/MavenPro[wght].ttf";
    private static final List<String> MAVEN_PRO_FONT_NAMES = List.of(
            "Maven Pro",
            "MavenPro[wght]",
            "MavenPro",
            "Maven Pro Regular"
    );

    @Value("${app.pdf.enforce-maven-pro-font:true}")
    private boolean enforceMavenProFont;

    public byte[] convert(byte[] docxBytes) throws Exception {
        if (docxBytes == null || docxBytes.length == 0) {
            throw new IllegalArgumentException("No se puede convertir un DOCX vacío");
        }

        byte[] normalizedDocxBytes = normalizeDocxForDocx4j(docxBytes);
        WordprocessingMLPackage wordPackage = WordprocessingMLPackage.load(new ByteArrayInputStream(normalizedDocxBytes));
        if (enforceMavenProFont) {
            applyFontMapper(wordPackage);
        }

        FOSettings foSettings = Docx4J.createFOSettings();
        foSettings.setWmlPackage(wordPackage);
        configureFopSettings(foSettings, wordPackage);

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Docx4J.toFO(foSettings, out, Docx4J.FLAG_EXPORT_PREFER_XSL);
            return out.toByteArray();
        }
    }

    private void applyFontMapper(WordprocessingMLPackage wordPackage) throws Exception {
        Mapper fontMapper = new IdentityPlusMapper();
        PhysicalFont mavenPro = resolveMavenProFont();
        if (mavenPro != null) {
            for (String fontName : MAVEN_PRO_FONT_NAMES) {
                fontMapper.put(fontName, mavenPro);
            }
        }
        wordPackage.setFontMapper(fontMapper);
    }

    private PhysicalFont resolveMavenProFont() {
        for (String fontName : MAVEN_PRO_FONT_NAMES) {
            PhysicalFont existing = PhysicalFonts.get(fontName);
            if (existing != null) {
                return existing;
            }
        }

        ClassPathResource fontResource = new ClassPathResource(MAVEN_PRO_RESOURCE_PATH);
        if (!fontResource.exists()) {
            log.warn("No se encontró Maven Pro en classpath: {}", MAVEN_PRO_RESOURCE_PATH);
            return null;
        }

        try (InputStream in = fontResource.getInputStream()) {
            Path tempFont = Files.createTempFile("maven-pro-", ".ttf");
            Files.copy(in, tempFont, StandardCopyOption.REPLACE_EXISTING);
            tempFont.toFile().deleteOnExit();

            URI fontUri = tempFont.toUri();
            PhysicalFonts.addPhysicalFont(fontUri);
            PhysicalFonts.addPhysicalFonts("Maven Pro", fontUri);

            for (String fontName : MAVEN_PRO_FONT_NAMES) {
                PhysicalFont loaded = PhysicalFonts.get(fontName);
                if (loaded != null) {
                    return loaded;
                }
            }
        } catch (IOException ex) {
            log.warn("No se pudo cargar Maven Pro desde resources: {}", ex.getMessage());
        }

        log.warn("Maven Pro no quedó registrada en docx4j; se usará fuente sustituta");
        return null;
    }

    private void configureFopSettings(FOSettings foSettings, WordprocessingMLPackage wordPackage) {
        try {
            Mapper mapper = wordPackage.getFontMapper();
            if (mapper == null) {
                mapper = new IdentityPlusMapper();
                wordPackage.setFontMapper(mapper);
            }

            foSettings.setApacheFopMime(FOSettings.MIME_PDF);
            foSettings.setFopConfig(FopConfigUtil.createConfigurationObject(
                    mapper,
                    wordPackage.getMainDocumentPart().fontsInUse()
            ));
        } catch (Exception ex) {
            log.warn("No se pudo inicializar configuración FOP explícita: {}", ex.getMessage());
        }
    }

    private byte[] normalizeDocxForDocx4j(byte[] docxBytes) {
        try (ByteArrayInputStream input = new ByteArrayInputStream(docxBytes);
             ZipInputStream zipInput = new ZipInputStream(input);
             ByteArrayOutputStream output = new ByteArrayOutputStream();
             ZipOutputStream zipOutput = new ZipOutputStream(output)) {

            boolean modified = false;
            ZipEntry entry;
            while ((entry = zipInput.getNextEntry()) != null) {
                ZipEntry newEntry = new ZipEntry(entry.getName());
                zipOutput.putNextEntry(newEntry);

                byte[] entryBytes = zipInput.readAllBytes();
                if (entry.getName().endsWith(".xml")) {
                    String xml = new String(entryBytes, StandardCharsets.UTF_8);
                    String normalized = xml
                            .replace("<w:start", "<w:left")
                            .replace("</w:start", "</w:left")
                            .replace("<w:end", "<w:right")
                            .replace("</w:end", "</w:right");
                    if (!normalized.equals(xml)) {
                        modified = true;
                        entryBytes = normalized.getBytes(StandardCharsets.UTF_8);
                    }
                }

                zipOutput.write(entryBytes);
                zipOutput.closeEntry();
                zipInput.closeEntry();
            }

            if (modified) {
                log.info("DOCX normalizado para docx4j: etiquetas w:start/w:end convertidas a w:left/w:right");
                return output.toByteArray();
            }
        } catch (Exception ex) {
            log.warn("No se pudo normalizar el DOCX antes de docx4j: {}", ex.getMessage());
        }

        return docxBytes;
    }
}
