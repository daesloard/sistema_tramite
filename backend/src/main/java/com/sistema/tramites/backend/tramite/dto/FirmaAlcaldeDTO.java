package com.sistema.tramites.backend.tramite.dto;

public class FirmaAlcaldeDTO {

    private Long tramiteId;
    
    private String firmaDigital;  // Firma en base64 o token JWT

    private String username;

    public FirmaAlcaldeDTO() {}

    public FirmaAlcaldeDTO(Long tramiteId, String firmaDigital) {
        this.tramiteId = tramiteId;
        this.firmaDigital = firmaDigital;
    }

    public Long getTramiteId() {
        return tramiteId;
    }

    public void setTramiteId(Long tramiteId) {
        this.tramiteId = tramiteId;
    }

    public String getFirmaDigital() {
        return firmaDigital;
    }

    public void setFirmaDigital(String firmaDigital) {
        this.firmaDigital = firmaDigital;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }
}
