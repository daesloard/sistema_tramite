package com.sistema.tramites.backend;

public class DocumentoDescargaDTO {
    private String nombreArchivo;
    private byte[] contenido;

    public DocumentoDescargaDTO(String nombreArchivo, byte[] contenido) {
        this.nombreArchivo = nombreArchivo;
        this.contenido = contenido;
    }

    public String getNombreArchivo() {
        return nombreArchivo;
    }

    public void setNombreArchivo(String nombreArchivo) {
        this.nombreArchivo = nombreArchivo;
    }

    public byte[] getContenido() {
        return contenido;
    }

    public void setContenido(byte[] contenido) {
        this.contenido = contenido;
    }
}
