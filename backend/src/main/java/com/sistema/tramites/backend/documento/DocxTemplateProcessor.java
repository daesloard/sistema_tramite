package com.sistema.tramites.backend.documento;

import com.sistema.tramites.backend.tramite.Tramite;
import com.sistema.tramites.backend.usuario.Usuario;
import com.sistema.tramites.backend.util.NumeroALetrasUtil;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class DocxTemplateProcessor {
    private final ResourceLoader resourceLoader;

    public DocxTemplateProcessor(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    public byte[] processTemplate(String templateName, Tramite tramite, boolean aprobado, String observacion) throws IOException {
        Resource templateResource = resourceLoader.getResource("classpath:templates/" + templateName);
        try (XWPFDocument document = new XWPFDocument(templateResource.getInputStream())) {

            Map<String, String> replacements = createReplacements(tramite, aprobado, observacion);

            // Replace in all paragraphs and runs
            List<XWPFParagraph> paragraphs = document.getParagraphs();
            for (XWPFParagraph paragraph : paragraphs) {
                replaceInParagraph(paragraph, replacements);
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            document.write(baos);
            baos.close();
            return baos.toByteArray();
        }
    }

    private Map<String, String> createReplacements(Tramite tramite, boolean aprobado, String observacion) {
        Map<String, String> reps = new HashMap<>();
        LocalDateTime firmaDate = tramite.getFechaFirmaAlcalde();
        LocalDate firmaLocalDate = firmaDate != null ? firmaDate.toLocalDate() : LocalDate.now();

        // Solo los marcadores especificados por el usuario:
        reps.put("consecutivo", safeValue(tramite.getConsecutivoVerificador()));
        reps.put("nombreSolicitante", safeValue(tramite.getNombreSolicitante()));
        reps.put("numeroDocumento", safeValue(tramite.getNumeroDocumento()));
        reps.put("lugarExpedicionDocumento", safeValue(tramite.getLugarExpedicionDocumento()));
        reps.put("direccionResidencia", safeValue(tramite.getDireccionResidencia()));
        reps.put("dias", String.valueOf(firmaLocalDate.getDayOfMonth()));
        reps.put("diasLetras", NumeroALetrasUtil.numeroALetras(firmaLocalDate.getDayOfMonth()));
        reps.put("mesLetras", NumeroALetrasUtil.mesALetras(firmaLocalDate));
        reps.put("año", String.valueOf(firmaLocalDate.getYear()));
        reps.put("añoLetra", NumeroALetrasUtil.anioALetras(firmaLocalDate.getYear()));
        reps.put("firma.jpeg", ""); // El valor real se inserta en DocumentoGeneradoService
        reps.put("alcalde", safeNombreCompleto(tramite.getUsuarioAlcalde()));
        reps.put("verificador", safeNombreCompleto(tramite.getUsuarioVerificador()));
        reps.put("numeroRadico", safeValue(tramite.getNumeroRadicado()));
        reps.put("fechaFirma", firmaDate != null ? firmaDate.toString() : "");
        reps.put("observacion", safeValue(tramite.getObservaciones()));
        // Si hay otros marcadores definidos por el usuario, agrégalos aquí

        return reps;
    }

    private void replaceInParagraph(XWPFParagraph paragraph, Map<String, String> replacements) {
        // Reemplazo robusto de marcadores
        StringBuilder fullText = new StringBuilder();
        for (XWPFRun run : paragraph.getRuns()) {
            String runText = run.getText(0);
            if (runText != null) {
                for (Map.Entry<String, String> entry : replacements.entrySet()) {
                    String marcador = "<<" + entry.getKey() + ">>";
                    runText = runText.replace(marcador, entry.getValue());
                }
                fullText.append(runText);
            }
        }

        // Clear runs and set new text
        clearRuns(paragraph);
        if (!fullText.toString().isEmpty()) {
            XWPFRun newRun = paragraph.createRun();
            newRun.setText(fullText.toString());
            // Aplica fuente Maven Pro
            try {
                newRun.setFontFamily("Maven Pro");
            } catch (Exception ignored) {}
        }
    }

    private void clearRuns(XWPFParagraph paragraph) {
        while (!paragraph.getRuns().isEmpty()) {
            paragraph.removeRun(0);
        }
    }

    private String safeValue(String value) {
        if (value == null) return "";
        String v = value.trim();
        return v.equalsIgnoreCase("null") ? "" : v;
    }

    private String safeNombreCompleto(Usuario usuario) {
        if (usuario == null) return "Verificador/Alcalde";
        return safeValue(usuario.getNombreCompleto());
    }
}

