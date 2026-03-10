package com.sistema.tramites.backend.tramite;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;

@Service
public class ExportService {

    private final TramiteRepository tramiteRepository;

    public ExportService(TramiteRepository tramiteRepository) {
        this.tramiteRepository = tramiteRepository;
    }

    @Transactional(readOnly = true)
    public byte[] descargarConsolidadoVerificaciones() {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Consolidado Verificaciones");
            Row header = sheet.createRow(0);
            String[] colNames = {"Radicado", "Consecutivo Verificador", "Solicitante", "Documento", "Tipo Certificado", "Estado", "Fecha Radicación", "Fecha Firma Alcalde", "Observaciones"};
            for (int i = 0; i < colNames.length; i++) header.createCell(i).setCellValue(colNames[i]);

            List<Tramite> tramites = tramiteRepository.findAll().stream().sorted(Comparator.comparing(Tramite::getFechaRadicacion)).toList();
            int rowIndex = 1;
            for (Tramite t : tramites) {
                Row row = sheet.createRow(rowIndex++);
                row.createCell(0).setCellValue(valor(t.getNumeroRadicado()));
                row.createCell(1).setCellValue(valor(t.getConsecutivoVerificador()));
                row.createCell(2).setCellValue(valor(t.getNombreSolicitante()));
                row.createCell(3).setCellValue(valor(t.getNumeroDocumento()));
                row.createCell(4).setCellValue(valor(t.getTipo_certificado()));
                row.createCell(5).setCellValue(t.getEstado() != null ? t.getEstado().name() : "");
                row.createCell(6).setCellValue(t.getFechaRadicacion() != null ? t.getFechaRadicacion().toString() : "");
                row.createCell(7).setCellValue(t.getFechaFirmaAlcalde() != null ? t.getFechaFirmaAlcalde().toString() : "");
                row.createCell(8).setCellValue(valor(t.getObservaciones()));
            }
            for (int i = 0; i < colNames.length; i++) sheet.autoSizeColumn(i);
            workbook.write(outputStream);
            return outputStream.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Error al generar Excel", e);
        }
    }

    private String valor(String valor) { return valor == null ? "" : valor; }
}
