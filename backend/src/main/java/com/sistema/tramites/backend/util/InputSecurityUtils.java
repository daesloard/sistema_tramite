package com.sistema.tramites.backend.util;

import java.util.Locale;
import java.util.Set;

public final class InputSecurityUtils {

    private static final Set<String> DOCUMENT_TYPES = Set.of("sisben", "electoral", "residencia", "identidad", "solicitud");
    private static final Set<String> DOCUMENT_ACTIONS = Set.of("ver", "visualizar", "open", "descargar", "download");
    private static final Set<String> FACTOR_TYPES = Set.of("PRIMER_NOMBRE", "ULTIMOS_3_DOCUMENTO");

    private InputSecurityUtils() {
    }

    public static String normalizeDocumentType(String value) {
        String normalized = normalizeLower(value, 30, "Tipo de documento requerido");
        if (!DOCUMENT_TYPES.contains(normalized)) {
            throw new IllegalArgumentException("Tipo de documento no válido");
        }
        return normalized;
    }

    public static String normalizeDocumentAction(String value) {
        if (value == null || value.isBlank()) {
            return "descargar";
        }
        String normalized = normalizeLower(value, 20, "Acción inválida");
        if (!DOCUMENT_ACTIONS.contains(normalized)) {
            throw new IllegalArgumentException("Acción no válida");
        }
        return normalized;
    }

    public static String normalizeFactorType(String value) {
        String normalized = normalizeUpper(value, 40, "Factor de validación requerido");
        if (!FACTOR_TYPES.contains(normalized)) {
            throw new IllegalArgumentException("Factor de validación no permitido");
        }
        return normalized;
    }

    public static String validateFactorValue(String value, int maxLength) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("Valor del factor requerido");
        }
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException("Valor del factor excede la longitud permitida");
        }
        return normalized;
    }

    public static String validateRadicadoOrHash(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("Radicado o hash requerido");
        }
        if (normalized.length() > 120 || !normalized.matches("[A-Za-z0-9_-]+")) {
            throw new IllegalArgumentException("Radicado o hash con formato inválido");
        }
        return normalized;
    }

    public static String sanitizeSqlIdentifier(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " vacío");
        }
        String normalized = value.trim();
        if (!normalized.matches("[A-Za-z0-9_]+")) {
            throw new IllegalArgumentException(fieldName + " contiene caracteres no permitidos");
        }
        return normalized;
    }

    private static String normalizeLower(String value, int maxLength, String emptyMessage) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(emptyMessage);
        }
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException("Valor excede la longitud permitida");
        }
        return normalized;
    }

    private static String normalizeUpper(String value, int maxLength, String emptyMessage) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(emptyMessage);
        }
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException("Valor excede la longitud permitida");
        }
        return normalized;
    }
}