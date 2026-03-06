package com.sistema.tramites.backend;

import java.time.LocalDate;
import java.time.LocalDateTime;

public interface TramiteResumenView {
    Long getId();
    String getNumeroRadicado();
    String getNombreSolicitante();
    String getTipoTramite();
    EstadoTramite getEstado();
    LocalDateTime getFechaRadicacion();
    LocalDate getFechaVencimiento();
    LocalDate getFechaVigencia();
    LocalDateTime getFechaVerificacion();
    Boolean getVerificacionAprobada();
    LocalDateTime getFechaFirmaAlcalde();
    String getTipoDocumento();
    String getNumeroDocumento();
    String getLugarExpedicionDocumento();
    String getDireccionResidencia();
    String getBarrioResidencia();
    String getTelefono();
    String getCorreoElectronico();
    String getTipo_certificado();
    String getObservaciones();
    String getConsecutivoVerificador();
    String getRuta_certificado_final();
}
