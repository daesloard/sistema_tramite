package com.sistema.tramites.backend;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Email;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
public class Tramite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String numeroRadicado;

    @NotBlank
    @Column(nullable = false)
    private String nombreSolicitante;

    @NotBlank
    @Column(nullable = false)
    private String tipoTramite;

    @Column(length = 2000)
    private String descripcion;

    @Column(nullable = false)
    private LocalDateTime fechaRadicacion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoTramite estado;
    
    // Campos para Certificado de Residencia
    @Column
    private String tipoDocumento;

    @Column
    private String numeroDocumento;

    @Column
    private String lugarExpedicionDocumento;

    @Column
    private String direccionResidencia;

    @Column
    private String barrioResidencia;

    @Column
    private String telefono;

    @Email
    @Column
    private String correoElectronico;

    @Column
    private String ruta_documento_solicitud;

    @Column
    private String ruta_documento_identidad;

    @Column
    private String ruta_certificado_sisben;

    @Column
    private String ruta_certificado_electoral;

    @Column
    private String tipo_certificado;  // SISBEN, ELECTORAL, JAC

    @Column
    private String ruta_certificado;

    @Column
    private String ruta_certificado_final;

    @Column
    private String driveFolderId;

    @Column
    private LocalDate fechaVencimiento;

    @Column
    private LocalDate fechaVigencia;

    @Column(length = 2000)
    private String observaciones;

    @Column
    private String consecutivoVerificador;

    @Column
    private LocalDateTime fechaVerificacion;

    @Column
    private Boolean verificacionAprobada;

    @Column
    private String firmaAlcalde;  // Firma digital

    @Column
    private LocalDateTime fechaFirmaAlcalde;

    @ManyToOne
    @JoinColumn(name = "usuario_verificador_id")
    private Usuario usuarioVerificador;

    @ManyToOne
    @JoinColumn(name = "usuario_alcalde_id")
    private Usuario usuarioAlcalde;

    // Campos para almacenamiento de documentos en BD (BLOB)
    @Lob
    @Column(columnDefinition = "LONGBLOB")
    private byte[] contenidoCertificadoSisben;

    @Column
    private String nombreArchivoSisben;

    @Column
    private String tipoContenidoSisben;

    @Lob
    @Column(columnDefinition = "LONGBLOB")
    private byte[] contenidoCertificadoElectoral;

    @Column
    private String nombreArchivoElectoral;

    @Column
    private String tipoContenidoElectoral;

    @Lob
    @Column(columnDefinition = "LONGBLOB")
    private byte[] contenidoDocumentoResidencia;

    @Column
    private String nombreArchivoResidencia;

    @Column
    private String tipoContenidoResidencia;

    // Documentos de identidad y solicitud
    @Lob
    @Column(columnDefinition = "LONGBLOB")
    private byte[] contenidoDocumentoIdentidad;

    @Column
    private String nombreArchivoIdentidad;

    @Column
    private String tipoContenidoIdentidad;

    @Lob
    @Column(columnDefinition = "LONGBLOB")
    private byte[] contenidoDocumentoSolicitud;

    @Column
    private String nombreArchivoSolicitud;

    @Column
    private String tipoContenidoSolicitud;

    @Lob
    @Column(columnDefinition = "LONGBLOB")
    private byte[] contenidoPdfGenerado;

    @Column
    private String nombrePdfGenerado;

    @Column
    private String tipoContenidoPdfGenerado;

    @Column(length = 32)
    private String motorPdfGenerado;

    @Column(unique = true)
    private String codigoVerificacion;

    @Column(length = 128)
    private String hashDocumentoGenerado;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNumeroRadicado() {
        return numeroRadicado;
    }

    public void setNumeroRadicado(String numeroRadicado) {
        this.numeroRadicado = numeroRadicado;
    }

    public String getNombreSolicitante() {
        return nombreSolicitante;
    }

    public void setNombreSolicitante(String nombreSolicitante) {
        this.nombreSolicitante = nombreSolicitante;
    }

    public String getTipoTramite() {
        return tipoTramite;
    }

    public void setTipoTramite(String tipoTramite) {
        this.tipoTramite = tipoTramite;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public LocalDateTime getFechaRadicacion() {
        return fechaRadicacion;
    }

    public void setFechaRadicacion(LocalDateTime fechaRadicacion) {
        this.fechaRadicacion = fechaRadicacion;
    }

    public EstadoTramite getEstado() {
        return estado;
    }

    public void setEstado(EstadoTramite estado) {
        this.estado = estado;
    }

    public String getTipoDocumento() {
        return tipoDocumento;
    }

    public void setTipoDocumento(String tipoDocumento) {
        this.tipoDocumento = tipoDocumento;
    }

    public String getNumeroDocumento() {
        return numeroDocumento;
    }

    public void setNumeroDocumento(String numeroDocumento) {
        this.numeroDocumento = numeroDocumento;
    }

    public String getLugarExpedicionDocumento() {
        return lugarExpedicionDocumento;
    }

    public void setLugarExpedicionDocumento(String lugarExpedicionDocumento) {
        this.lugarExpedicionDocumento = lugarExpedicionDocumento;
    }

    public String getDireccionResidencia() {
        return direccionResidencia;
    }

    public void setDireccionResidencia(String direccionResidencia) {
        this.direccionResidencia = direccionResidencia;
    }

    public String getBarrioResidencia() {
        return barrioResidencia;
    }

    public void setBarrioResidencia(String barrioResidencia) {
        this.barrioResidencia = barrioResidencia;
    }

    public String getTelefono() {
        return telefono;
    }

    public void setTelefono(String telefono) {
        this.telefono = telefono;
    }

    public String getCorreoElectronico() {
        return correoElectronico;
    }

    public void setCorreoElectronico(String correoElectronico) {
        this.correoElectronico = correoElectronico;
    }

    public String getRuta_documento_solicitud() {
        return ruta_documento_solicitud;
    }

    public void setRuta_documento_solicitud(String ruta_documento_solicitud) {
        this.ruta_documento_solicitud = ruta_documento_solicitud;
    }

    public String getRuta_documento_identidad() {
        return ruta_documento_identidad;
    }

    public void setRuta_documento_identidad(String ruta_documento_identidad) {
        this.ruta_documento_identidad = ruta_documento_identidad;
    }

    public String getTipo_certificado() {
        return tipo_certificado;
    }

    public void setTipo_certificado(String tipo_certificado) {
        this.tipo_certificado = tipo_certificado;
    }

    public String getRuta_certificado() {
        return ruta_certificado;
    }

    public void setRuta_certificado(String ruta_certificado) {
        this.ruta_certificado = ruta_certificado;
    }

    public String getRuta_certificado_final() {
        return ruta_certificado_final;
    }

    public void setRuta_certificado_final(String ruta_certificado_final) {
        this.ruta_certificado_final = ruta_certificado_final;
    }

    public String getDriveFolderId() {
        return driveFolderId;
    }

    public void setDriveFolderId(String driveFolderId) {
        this.driveFolderId = driveFolderId;
    }

    public LocalDate getFechaVencimiento() {
        return fechaVencimiento;
    }

    public void setFechaVencimiento(LocalDate fechaVencimiento) {
        this.fechaVencimiento = fechaVencimiento;
    }

    public LocalDate getFechaVigencia() {
        return fechaVigencia;
    }

    public void setFechaVigencia(LocalDate fechaVigencia) {
        this.fechaVigencia = fechaVigencia;
    }

    public String getObservaciones() {
        return observaciones;
    }

    public void setObservaciones(String observaciones) {
        this.observaciones = observaciones;
    }

    public String getConsecutivoVerificador() {
        return consecutivoVerificador;
    }

    public void setConsecutivoVerificador(String consecutivoVerificador) {
        this.consecutivoVerificador = consecutivoVerificador;
    }

    public LocalDateTime getFechaVerificacion() {
        return fechaVerificacion;
    }

    public void setFechaVerificacion(LocalDateTime fechaVerificacion) {
        this.fechaVerificacion = fechaVerificacion;
    }

    public Boolean getVerificacionAprobada() {
        return verificacionAprobada;
    }

    public void setVerificacionAprobada(Boolean verificacionAprobada) {
        this.verificacionAprobada = verificacionAprobada;
    }

    public String getFirmaAlcalde() {
        return firmaAlcalde;
    }

    public void setFirmaAlcalde(String firmaAlcalde) {
        this.firmaAlcalde = firmaAlcalde;
    }

    public LocalDateTime getFechaFirmaAlcalde() {
        return fechaFirmaAlcalde;
    }

    public void setFechaFirmaAlcalde(LocalDateTime fechaFirmaAlcalde) {
        this.fechaFirmaAlcalde = fechaFirmaAlcalde;
    }

    public String getRuta_certificado_sisben() {
        return ruta_certificado_sisben;
    }

    public void setRuta_certificado_sisben(String ruta_certificado_sisben) {
        this.ruta_certificado_sisben = ruta_certificado_sisben;
    }

    public String getRuta_certificado_electoral() {
        return ruta_certificado_electoral;
    }

    public void setRuta_certificado_electoral(String ruta_certificado_electoral) {
        this.ruta_certificado_electoral = ruta_certificado_electoral;
    }

    public Usuario getUsuarioVerificador() {
        return usuarioVerificador;
    }

    public void setUsuarioVerificador(Usuario usuarioVerificador) {
        this.usuarioVerificador = usuarioVerificador;
    }

    public Usuario getUsuarioAlcalde() {
        return usuarioAlcalde;
    }

    public void setUsuarioAlcalde(Usuario usuarioAlcalde) {
        this.usuarioAlcalde = usuarioAlcalde;
    }

    // Getters y Setters para BLOB de SISBEN
    public byte[] getContenidoCertificadoSisben() {
        return contenidoCertificadoSisben;
    }

    public void setContenidoCertificadoSisben(byte[] contenidoCertificadoSisben) {
        this.contenidoCertificadoSisben = contenidoCertificadoSisben;
    }

    public String getNombreArchivoSisben() {
        return nombreArchivoSisben;
    }

    public void setNombreArchivoSisben(String nombreArchivoSisben) {
        this.nombreArchivoSisben = nombreArchivoSisben;
    }

    public String getTipoContenidoSisben() {
        return tipoContenidoSisben;
    }

    public void setTipoContenidoSisben(String tipoContenidoSisben) {
        this.tipoContenidoSisben = tipoContenidoSisben;
    }

    // Getters y Setters para BLOB de Electoral
    public byte[] getContenidoCertificadoElectoral() {
        return contenidoCertificadoElectoral;
    }

    public void setContenidoCertificadoElectoral(byte[] contenidoCertificadoElectoral) {
        this.contenidoCertificadoElectoral = contenidoCertificadoElectoral;
    }

    public String getNombreArchivoElectoral() {
        return nombreArchivoElectoral;
    }

    public void setNombreArchivoElectoral(String nombreArchivoElectoral) {
        this.nombreArchivoElectoral = nombreArchivoElectoral;
    }

    public String getTipoContenidoElectoral() {
        return tipoContenidoElectoral;
    }

    public void setTipoContenidoElectoral(String tipoContenidoElectoral) {
        this.tipoContenidoElectoral = tipoContenidoElectoral;
    }

    // Getters y Setters para BLOB de Residencia
    public byte[] getContenidoDocumentoResidencia() {
        return contenidoDocumentoResidencia;
    }

    public void setContenidoDocumentoResidencia(byte[] contenidoDocumentoResidencia) {
        this.contenidoDocumentoResidencia = contenidoDocumentoResidencia;
    }

    public String getNombreArchivoResidencia() {
        return nombreArchivoResidencia;
    }

    public void setNombreArchivoResidencia(String nombreArchivoResidencia) {
        this.nombreArchivoResidencia = nombreArchivoResidencia;
    }

    public String getTipoContenidoResidencia() {
        return tipoContenidoResidencia;
    }

    public void setTipoContenidoResidencia(String tipoContenidoResidencia) {
        this.tipoContenidoResidencia = tipoContenidoResidencia;
    }

    // Getters y Setters para documentoIdentidad
    public byte[] getContenidoDocumentoIdentidad() {
        return contenidoDocumentoIdentidad;
    }

    public void setContenidoDocumentoIdentidad(byte[] contenidoDocumentoIdentidad) {
        this.contenidoDocumentoIdentidad = contenidoDocumentoIdentidad;
    }

    public String getNombreArchivoIdentidad() {
        return nombreArchivoIdentidad;
    }

    public void setNombreArchivoIdentidad(String nombreArchivoIdentidad) {
        this.nombreArchivoIdentidad = nombreArchivoIdentidad;
    }

    public String getTipoContenidoIdentidad() {
        return tipoContenidoIdentidad;
    }

    public void setTipoContenidoIdentidad(String tipoContenidoIdentidad) {
        this.tipoContenidoIdentidad = tipoContenidoIdentidad;
    }

    // Getters y Setters para documentoSolicitud
    public byte[] getContenidoDocumentoSolicitud() {
        return contenidoDocumentoSolicitud;
    }

    public void setContenidoDocumentoSolicitud(byte[] contenidoDocumentoSolicitud) {
        this.contenidoDocumentoSolicitud = contenidoDocumentoSolicitud;
    }

    public String getNombreArchivoSolicitud() {
        return nombreArchivoSolicitud;
    }

    public void setNombreArchivoSolicitud(String nombreArchivoSolicitud) {
        this.nombreArchivoSolicitud = nombreArchivoSolicitud;
    }

    public String getTipoContenidoSolicitud() {
        return tipoContenidoSolicitud;
    }

    public void setTipoContenidoSolicitud(String tipoContenidoSolicitud) {
        this.tipoContenidoSolicitud = tipoContenidoSolicitud;
    }

    public byte[] getContenidoPdfGenerado() {
        return contenidoPdfGenerado;
    }

    public void setContenidoPdfGenerado(byte[] contenidoPdfGenerado) {
        this.contenidoPdfGenerado = contenidoPdfGenerado;
    }

    public String getNombrePdfGenerado() {
        return nombrePdfGenerado;
    }

    public void setNombrePdfGenerado(String nombrePdfGenerado) {
        this.nombrePdfGenerado = nombrePdfGenerado;
    }

    public String getTipoContenidoPdfGenerado() {
        return tipoContenidoPdfGenerado;
    }

    public void setTipoContenidoPdfGenerado(String tipoContenidoPdfGenerado) {
        this.tipoContenidoPdfGenerado = tipoContenidoPdfGenerado;
    }

    public String getMotorPdfGenerado() {
        return motorPdfGenerado;
    }

    public void setMotorPdfGenerado(String motorPdfGenerado) {
        this.motorPdfGenerado = motorPdfGenerado;
    }

    public String getCodigoVerificacion() {
        return codigoVerificacion;
    }

    public void setCodigoVerificacion(String codigoVerificacion) {
        this.codigoVerificacion = codigoVerificacion;
    }

    public String getHashDocumentoGenerado() {
        return hashDocumentoGenerado;
    }

    public void setHashDocumentoGenerado(String hashDocumentoGenerado) {
        this.hashDocumentoGenerado = hashDocumentoGenerado;
    }
}
