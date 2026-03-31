package com.sistema.tramites.backend.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@Component
public class PdfServiceEndpointValidator {

    private static final Set<String> LOCAL_HOSTS = new HashSet<>(Arrays.asList(
            "localhost", "127.0.0.1", "::1"
    ));

    @Value("${app.pdf.gotenberg.url:}")
    private String gotenbergUrl;

    @Value("${app.pdf.gotenberg.enabled:false}")
    private boolean gotenbergEnabled;

    @Value("${app.docxtemplater.url:}")
    private String docxtemplaterUrl;

    @Value("${app.pdf.allow-localhost-services:false}")
    private boolean allowLocalhostServices;

    private final Environment environment;

    public PdfServiceEndpointValidator(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    public void validate() {
        // Solo validar la URL de Gotenberg si el motor Gotenberg está habilitado explícitamente.
        // Con app.pdf.gotenberg.enabled=false, la conversión la hace docx4j (puro Java).
        if (gotenbergEnabled) {
            validateUrl("app.pdf.gotenberg.url", gotenbergUrl);
        }
        validateUrl("app.docxtemplater.url", docxtemplaterUrl);
    }

    private void validateUrl(String propertyName, String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new IllegalStateException("Configuracion obligatoria faltante: " + propertyName);
        }

        URI uri;
        try {
            uri = URI.create(rawUrl);
        } catch (Exception ex) {
            throw new IllegalStateException("URL invalida en " + propertyName + ": " + rawUrl, ex);
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalStateException("URL sin host valido en " + propertyName + ": " + rawUrl);
        }

        if (!allowLocalhostServices && isLocalHost(host) && !isDevProfileActive()) {
            throw new IllegalStateException(
                    "No se permite localhost en " + propertyName + " fuera de desarrollo: " + rawUrl
            );
        }
    }

    private boolean isLocalHost(String host) {
        String normalized = host.trim().toLowerCase();
        return LOCAL_HOSTS.contains(normalized);
    }

    private boolean isDevProfileActive() {
        return Arrays.stream(environment.getActiveProfiles()).anyMatch("dev"::equalsIgnoreCase);
    }
}
