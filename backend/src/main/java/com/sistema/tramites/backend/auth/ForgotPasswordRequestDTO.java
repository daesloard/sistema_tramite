package com.sistema.tramites.backend.auth;

public class ForgotPasswordRequestDTO {
    private String usuarioOEmail;

    public ForgotPasswordRequestDTO() {
    }

    public String getUsuarioOEmail() {
        return usuarioOEmail;
    }

    public void setUsuarioOEmail(String usuarioOEmail) {
        this.usuarioOEmail = usuarioOEmail;
    }
}
