package com.sistema.tramites.backend.documento;

import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

@Component
public class DocxTemplateProcessor {

    private static final String[] SIGNATURE_PLACEHOLDERS = {
            "{{firma}}",
            "{{firma.jpg}}",
            "{{firma.jpeg}}",
            "<<firma>>",
            "<<firma.jpg>>",
            "<<firma.jpeg>>"
    };

    private final ResourceLoader resourceLoader;

    public DocxTemplateProcessor(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    public byte[] processTemplate(String templateName, Map<String, String> replacements, byte[] signatureBytes) throws IOException {
        Resource templateResource = resourceLoader.getResource("classpath:templates/" + templateName);
        if (!templateResource.exists()) {
            throw new IOException("Plantilla no encontrada en classpath:templates/" + templateName);
        }

        try (XWPFDocument document = new XWPFDocument(templateResource.getInputStream());
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            processParagraphs(document.getParagraphs(), replacements, signatureBytes);
            processTables(document.getTables(), replacements, signatureBytes);

            for (XWPFHeader header : document.getHeaderList()) {
                processParagraphs(header.getParagraphs(), replacements, signatureBytes);
                processTables(header.getTables(), replacements, signatureBytes);
            }

            for (XWPFFooter footer : document.getFooterList()) {
                processParagraphs(footer.getParagraphs(), replacements, signatureBytes);
                processTables(footer.getTables(), replacements, signatureBytes);
            }

            document.write(baos);
            return baos.toByteArray();
        }
    }

    private void processTables(List<XWPFTable> tables, Map<String, String> replacements, byte[] signatureBytes) {
        for (XWPFTable table : tables) {
            for (XWPFTableRow row : table.getRows()) {
                for (XWPFTableCell cell : row.getTableCells()) {
                    processParagraphs(cell.getParagraphs(), replacements, signatureBytes);
                    processTables(cell.getTables(), replacements, signatureBytes);
                }
            }
        }
    }

    private void processParagraphs(List<XWPFParagraph> paragraphs, Map<String, String> replacements, byte[] signatureBytes) {
        for (XWPFParagraph paragraph : paragraphs) {
            for (XWPFRun run : paragraph.getRuns()) {
                replaceInRun(run, replacements, signatureBytes);
            }
        }
    }

    private void replaceInRun(XWPFRun run, Map<String, String> replacements, byte[] signatureBytes) {
        String original = run.getText(0);
        if (original == null || original.isEmpty()) {
            return;
        }

        String updated = original;
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            String key = entry.getKey();
            if (key == null || key.isBlank() || key.toLowerCase().startsWith("firma")) {
                continue;
            }

            String value = entry.getValue() == null ? "" : entry.getValue();
            updated = updated.replace("{{" + key + "}}", value);
            updated = updated.replace("<<" + key + ">>", value);
        }

        int signatureOccurrences = countAndStripSignaturePlaceholders(updated);
        updated = stripSignaturePlaceholders(updated);

        if (!updated.equals(original)) {
            run.setText(updated, 0);
        }

        if (signatureOccurrences > 0 && signatureBytes != null && signatureBytes.length > 0) {
            for (int i = 0; i < signatureOccurrences; i++) {
                try {
                    run.addPicture(
                            new ByteArrayInputStream(signatureBytes),
                            XWPFDocument.PICTURE_TYPE_JPEG,
                            "firma.jpeg",
                            Units.toEMU(170),
                            Units.toEMU(52)
                    );
                } catch (Exception ignored) {
                    // Si falla la inserción de imagen, se conserva el resto del documento procesado.
                }
            }
        }
    }

    private int countAndStripSignaturePlaceholders(String text) {
        int count = 0;
        for (String placeholder : SIGNATURE_PLACEHOLDERS) {
            int from = 0;
            int idx;
            while ((idx = text.indexOf(placeholder, from)) >= 0) {
                count++;
                from = idx + placeholder.length();
            }
        }
        return count;
    }

    private String stripSignaturePlaceholders(String text) {
        String result = text;
        for (String placeholder : SIGNATURE_PLACEHOLDERS) {
            result = result.replace(placeholder, "");
        }
        return result;
    }
}

