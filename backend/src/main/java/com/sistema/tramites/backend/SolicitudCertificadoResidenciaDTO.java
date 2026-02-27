package com.sistema.tramites.backend;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Email;

public class SolicitudCertificadoResidenciaDTO {

    @NotBlank
    private String nombre;

    @NotBlank
    private String tipoDocumento;

    @NotBlank
    private String numeroDocumento;

    @NotBlank
    private String lugarExpedicionDocumento;

    @NotBlank
    private String direccionResidencia;

    @NotBlank
    private String barrioResidencia;

    @NotBlank
    private String telefono;

    @Email
    @NotBlank
    private String correoElectronico;

    // Documentos opcionales (pueden cargarse después)
    private String documento_solicitud_base64;

    private String documento_solicitud_nombre;

    private String documento_solicitud_tipo;

    private String documento_identidad_base64;

    private String documento_identidad_nombre;

    private String documento_identidad_tipo;

    @NotBlank
    private String tipo_certificado;  // SISBEN, ELECTORAL, JAC

    // Certificado se cargará por separado (arquivos binarios)
    private String certificado_base64;

    private String certificado_nombre;

    private String certificado_tipo;

    // Getters y Setters
    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
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

    public String getDocumento_solicitud_base64() {
        return documento_solicitud_base64;
    }

    public void setDocumento_solicitud_base64(String documento_solicitud_base64) {
        this.documento_solicitud_base64 = documento_solicitud_base64;
    }

    public String getDocumento_solicitud_nombre() {
        return documento_solicitud_nombre;
    }

    public void setDocumento_solicitud_nombre(String documento_solicitud_nombre) {
        this.documento_solicitud_nombre = documento_solicitud_nombre;
    }

    public String getDocumento_solicitud_tipo() {
        return documento_solicitud_tipo;
    }

    public void setDocumento_solicitud_tipo(String documento_solicitud_tipo) {
        this.documento_solicitud_tipo = documento_solicitud_tipo;
    }

    public String getDocumento_identidad_base64() {
        return documento_identidad_base64;
    }

    public void setDocumento_identidad_base64(String documento_identidad_base64) {
        this.documento_identidad_base64 = documento_identidad_base64;
    }

    public String getDocumento_identidad_nombre() {
        return documento_identidad_nombre;
    }

    public void setDocumento_identidad_nombre(String documento_identidad_nombre) {
        this.documento_identidad_nombre = documento_identidad_nombre;
    }

    public String getDocumento_identidad_tipo() {
        return documento_identidad_tipo;
    }

    public void setDocumento_identidad_tipo(String documento_identidad_tipo) {
        this.documento_identidad_tipo = documento_identidad_tipo;
    }

    public String getTipo_certificado() {
        return tipo_certificado;
    }

    public void setTipo_certificado(String tipo_certificado) {
        this.tipo_certificado = tipo_certificado;
    }

    public String getCertificado_base64() {
        return certificado_base64;
    }

    public void setCertificado_base64(String certificado_base64) {
        this.certificado_base64 = certificado_base64;
    }

    public String getCertificado_nombre() {
        return certificado_nombre;
    }

    public void setCertificado_nombre(String certificado_nombre) {
        this.certificado_nombre = certificado_nombre;
    }

    public String getCertificado_tipo() {
        return certificado_tipo;
    }

    public void setCertificado_tipo(String certificado_tipo) {
        this.certificado_tipo = certificado_tipo;
    }
}
