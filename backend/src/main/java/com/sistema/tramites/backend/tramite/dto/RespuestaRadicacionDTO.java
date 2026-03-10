package com.sistema.tramites.backend.tramite.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class RespuestaRadicacionDTO {

    private Long tramiteId;

    private String numeroRadicado;
    
    private LocalDateTime fechaSolicitud;
    
    private LocalDate fechaVencimiento;
    
    private String estado;
    
    private String mensaje;

    public RespuestaRadicacionDTO() {}

    public RespuestaRadicacionDTO(Long tramiteId, String numeroRadicado, LocalDateTime fechaSolicitud,
                                   LocalDate fechaVencimiento, String estado, String mensaje) {
        this.tramiteId = tramiteId;
        this.numeroRadicado = numeroRadicado;
        this.fechaSolicitud = fechaSolicitud;
        this.fechaVencimiento = fechaVencimiento;
        this.estado = estado;
        this.mensaje = mensaje;
    }

    public Long getTramiteId() {
        return tramiteId;
    }

    public void setTramiteId(Long tramiteId) {
        this.tramiteId = tramiteId;
    }

    public String getNumeroRadicado() {
        return numeroRadicado;
    }

    public void setNumeroRadicado(String numeroRadicado) {
        this.numeroRadicado = numeroRadicado;
    }

    public LocalDateTime getFechaSolicitud() {
        return fechaSolicitud;
    }

    public void setFechaSolicitud(LocalDateTime fechaSolicitud) {
        this.fechaSolicitud = fechaSolicitud;
    }

    public LocalDate getFechaVencimiento() {
        return fechaVencimiento;
    }

    public void setFechaVencimiento(LocalDate fechaVencimiento) {
        this.fechaVencimiento = fechaVencimiento;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public String getMensaje() {
        return mensaje;
    }

    public void setMensaje(String mensaje) {
        this.mensaje = mensaje;
    }
}
