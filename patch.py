import re

with open('DocumentoGeneradoService.java', 'r', encoding='utf-8') as f:
    content = f.read()

# Pattern to replace from generarPdfDesdePlantillaDocx to right before generarNombrePdf
pattern = re.compile(r'    private PdfGeneracionResultado generarPdfDesdePlantillaDocx.*?    private String generarNombrePdf', re.DOTALL)

replacement = """    private PdfGeneracionResultado generarPdfDesdePlantillaDocx(Tramite tramite, boolean aprobado, String observacion) {
        String nombrePlantilla = obtenerNombrePlantilla(tramite, aprobado);
        Resource resource = resourceLoader.getResource("classpath:templates/" + nombrePlantilla);

        try (InputStream inputStream = resource.getInputStream()) {
            com.spire.doc.Document document = new com.spire.doc.Document();
            document.loadFromStream(inputStream, com.spire.doc.FileFormat.Docx);

            reemplazarMarcadoresEnSpireDoc(document, tramite, observacion);
            insertarFirmaEnSpireDoc(document, tramite);

            ByteArrayOutputStream pdfOutput = new ByteArrayOutputStream();
            document.saveToStream(pdfOutput, com.spire.doc.FileFormat.PDF);
            
            return new PdfGeneracionResultado(pdfOutput.toByteArray(), "spire.doc");
        } catch (Exception e) {
            throw new IllegalStateException("No fue posible generar PDF desde plantilla DOCX usando Spire.Doc", e);
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

    private String generarNombrePdf"""

new_content = pattern.sub(replacement, content)

# Remove the unused imports
import_removals = [
    r'^import org\.docx4j\..*?;\n',
    r'^import org\.apache\.poi\..*?;\n',
    r'^import org\.openxmlformats\..*?;\n',
    r'^import java\.net\.http\.HttpClient;\n',
    r'^import java\.net\.http\.HttpRequest;\n',
    r'^import java\.net\.http\.HttpResponse;\n',
]

for imp in import_removals:
    new_content = re.sub(imp, '', new_content, flags=re.MULTILINE)

# Also fix the method that generates txt and html to not need Apache POI
# `leerTextoPlantilla` needs to be rewritten since it used POI
leer_texto_replacement = """    private String leerTextoPlantilla(String nombrePlantilla) {
        Resource resource = resourceLoader.getResource("classpath:templates/" + nombrePlantilla);
        try (InputStream inputStream = resource.getInputStream()) {
            com.spire.doc.Document document = new com.spire.doc.Document();
            document.loadFromStream(inputStream, com.spire.doc.FileFormat.Docx);
            return document.getText();
        } catch (Exception e) {
            throw new IllegalStateException("No fue posible leer la plantilla: " + nombrePlantilla, e);
        }
    }"""
new_content = re.sub(r'    private String leerTextoPlantilla.*?\}\n    }', leer_texto_replacement, new_content, flags=re.DOTALL)

with open('DocumentoGeneradoService.java', 'w', encoding='utf-8') as f:
    f.write(new_content)
print("Patched successfully")
