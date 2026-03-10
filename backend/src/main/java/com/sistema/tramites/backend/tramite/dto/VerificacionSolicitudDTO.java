package com.sistema.tramites.backend.tramite.dto;

import jakarta.validation.constraints.Pattern;

public class VerificacionSolicitudDTO {

    private Long tramiteId;
    
    private boolean aprobado;
    
    private String observaciones;

    @Pattern(regexp = "^\\d*$", message = "El consecutivo solo debe contener digitos")
    private String consecutivo;

    private String username;

    public VerificacionSolicitudDTO() {}

    public VerificacionSolicitudDTO(Long tramiteId, boolean aprobado, String observaciones) {
        this.tramiteId = tramiteId;
        this.aprobado = aprobado;
        this.observaciones = observaciones;
    }

    public Long getTramiteId() {
        return tramiteId;
    }

    public void setTramiteId(Long tramiteId) {
        this.tramiteId = tramiteId;
    }

    public boolean isAprobado() {
        return aprobado;
    }

    public void setAprobado(boolean aprobado) {
        this.aprobado = aprobado;
    }

    public String getObservaciones() {
        return observaciones;
    }

    public void setObservaciones(String observaciones) {
        this.observaciones = observaciones;
    }

    public String getConsecutivo() {
        return consecutivo;
    }

    public void setConsecutivo(String consecutivo) {
        this.consecutivo = consecutivo;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }
}
