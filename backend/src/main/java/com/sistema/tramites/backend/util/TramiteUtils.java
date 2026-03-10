package com.sistema.tramites.backend.util;

import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Component
public class TramiteUtils {

    public String generarRadicado() {
        return "RES-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
    }

    public String generarCodigoVerificacion(String numeroRadicado) {
        String base = (numeroRadicado == null ? "TRAM" : numeroRadicado.replaceAll("[^A-Za-z0-9]", "").toUpperCase());
        String sufijo = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyMMddHHmmss"));
        String prefijo = base.length() <= 8 ? base : base.substring(base.length() - 8);
        return prefijo + "-" + sufijo;
    }

    public LocalDate obtenerFechaRadicacion(LocalDateTime fechaRadicacion) {
        return fechaRadicacion != null ? fechaRadicacion.toLocalDate() : LocalDate.now();
    }

    public String normalizarCorreo(String correo) {
        return (correo == null || correo.trim().isBlank()) ? null : correo.trim().toLowerCase(Locale.ROOT);
    }
}
