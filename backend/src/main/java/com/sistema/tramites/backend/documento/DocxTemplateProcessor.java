package com.sistema.tramites.backend.documento;

import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.*;
import org.apache.xmlbeans.XmlCursor;
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
            "{{FIRMA}}",
            "{{FIRMA.JPG}}",
            "{{FIRMA.JPEG}}",
            "<<firma>>",
            "<<firma.jpg>>",
            "<<firma.jpeg>>",
            "((firma))",
            "((firma.jpg))",
            "((firma.jpeg))",
            "((FIRMA))",
            "((FIRMA.JPG))",
            "((FIRMA.JPEG))"
    };

        private static final java.util.regex.Pattern SIGNATURE_PATTERN = java.util.regex.Pattern.compile(
            "(?i)(\\{\\{\\s*firma(?:\\.jpe?g)?\\s*\\}\\}|<<\\s*firma(?:\\.jpe?g)?\\s*>>|\\(\\(\\s*firma(?:\\.jpe?g)?\\s*\\)\\))"
        );

    private final ResourceLoader resourceLoader;

    public DocxTemplateProcessor(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    public byte[] processTemplate(String templateName, Map<String, String> replacements, byte[] signatureBytes) throws IOException {
        Resource templateResource = resourceLoader.getResource("classpath:templates/" + templateName);
        if (!templateResource.exists()) {
            throw new IOException("Plantilla no encontrada en classpath:templates/" + templateName);
        }

        try (ByteArrayOutputStream templateBytes = new ByteArrayOutputStream()) {
            templateResource.getInputStream().transferTo(templateBytes);
            return processDocument(templateBytes.toByteArray(), replacements, signatureBytes);
        }
    }

    public byte[] processDocument(byte[] docxBytes, Map<String, String> replacements, byte[] signatureBytes) throws IOException {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(docxBytes));
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
            replaceInParagraph(paragraph, replacements, signatureBytes);
        }
    }

    private void replaceInParagraph(XWPFParagraph paragraph, Map<String, String> replacements, byte[] signatureBytes) {
        List<XWPFRun> runs = paragraph.getRuns();
        if (runs == null || runs.isEmpty()) {
            return;
        }

        StringBuilder originalBuilder = new StringBuilder();
        for (XWPFRun run : runs) {
            String text = run.getText(0);
            if (text != null) {
                originalBuilder.append(text);
            }
        }

        String original = originalBuilder.toString();
        if (original.isEmpty()) {
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

        int signatureOccurrences = countSignaturePlaceholders(updated);
        updated = stripSignaturePlaceholders(updated);

        if (!updated.equals(original) || signatureOccurrences > 0) {
            for (XWPFRun run : runs) {
                run.setText("", 0);
            }
            XWPFRun firstRun = runs.get(0);
            firstRun.setText(updated, 0);

            if (signatureOccurrences > 0 && signatureBytes != null && signatureBytes.length > 0) {
                // Evita que la imagen quede recortada cuando la plantilla define interlineado exacto.
                paragraph.setSpacingLineRule(LineSpacingRule.AUTO);
                paragraph.setSpacingBetween(1.0);
                for (int i = 0; i < signatureOccurrences; i++) {
                    try {
                        if (!insertSignatureInNewParagraph(paragraph, signatureBytes)) {
                            firstRun.addPicture(
                                    new ByteArrayInputStream(signatureBytes),
                                    XWPFDocument.PICTURE_TYPE_JPEG,
                                    "firma.jpeg",
                                    Units.toEMU(170),
                                    Units.toEMU(52)
                            );
                        }
                    } catch (Exception e) {
                        // Si falla la inserción en un marcador, se conserva el resto del documento.
                    }
                }
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

        int signatureOccurrences = countSignaturePlaceholders(updated);
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

    private int countSignaturePlaceholders(String text) {
        int count = 0;
        java.util.regex.Matcher matcher = SIGNATURE_PATTERN.matcher(text);
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    private String stripSignaturePlaceholders(String text) {
        String result = SIGNATURE_PATTERN.matcher(text).replaceAll("");
        for (String placeholder : SIGNATURE_PLACEHOLDERS) {
            result = result.replace(placeholder, "");
        }
        return result;
    }

    private boolean insertSignatureInNewParagraph(XWPFParagraph paragraph, byte[] signatureBytes) {
        IBody body = paragraph.getBody();
        XmlCursor cursor = paragraph.getCTP().newCursor();
        try {
            cursor.toEndToken();

            XWPFParagraph signatureParagraph = null;
            if (body instanceof XWPFDocument document) {
                signatureParagraph = document.insertNewParagraph(cursor);
            } else if (body instanceof XWPFTableCell cell) {
                signatureParagraph = cell.insertNewParagraph(cursor);
            } else if (body instanceof XWPFHeader header) {
                signatureParagraph = header.insertNewParagraph(cursor);
            } else if (body instanceof XWPFFooter footer) {
                signatureParagraph = footer.insertNewParagraph(cursor);
            }

            if (signatureParagraph == null) {
                return false;
            }

            signatureParagraph.setSpacingLineRule(LineSpacingRule.AUTO);
            signatureParagraph.setSpacingBetween(1.0);
            signatureParagraph.setAlignment(ParagraphAlignment.LEFT);

            XWPFRun imageRun = signatureParagraph.createRun();
            imageRun.addPicture(
                    new ByteArrayInputStream(signatureBytes),
                    XWPFDocument.PICTURE_TYPE_JPEG,
                    "firma.jpeg",
                    Units.toEMU(170),
                    Units.toEMU(52)
            );

            return true;
        } catch (Exception ignored) {
            return false;
        } finally {
            cursor.dispose();
        }
    }
}

