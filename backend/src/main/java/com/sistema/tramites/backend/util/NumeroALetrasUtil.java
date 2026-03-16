package com.sistema.tramites.backend.util;

import java.time.LocalDate;
import java.time.Month;
import java.util.HashMap;
import java.util.Map;
import java.util.Locale;

public class NumeroALetrasUtil {
    private static final Map<Integer, String> UNIDADES = new HashMap<>();
    private static final Map<Integer, String> DECENAS = new HashMap<>();
    private static final String[] MESES = {"enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre"};

    static {
        UNIDADES.put(1, "uno"); UNIDADES.put(2, "dos"); UNIDADES.put(3, "tres"); UNIDADES.put(4, "cuatro");
        UNIDADES.put(5, "cinco"); UNIDADES.put(6, "seis"); UNIDADES.put(7, "siete"); UNIDADES.put(8, "ocho");
        UNIDADES.put(9, "nueve"); UNIDADES.put(10, "diez"); UNIDADES.put(11, "once"); UNIDADES.put(12, "doce");
        UNIDADES.put(13, "trece"); UNIDADES.put(14, "catorce"); UNIDADES.put(15, "quince"); UNIDADES.put(16, "dieciséis");
        UNIDADES.put(17, "diecisiete"); UNIDADES.put(18, "dieciocho"); UNIDADES.put(19, "diecinueve");
        UNIDADES.put(20, "veinte"); UNIDADES.put(21, "veintiuno"); UNIDADES.put(22, "veintidós"); UNIDADES.put(23, "veintitrés");
        UNIDADES.put(24, "veinticuatro"); UNIDADES.put(25, "veinticinco"); UNIDADES.put(26, "veintiséis");
        UNIDADES.put(27, "veintisiete"); UNIDADES.put(28, "veintiocho"); UNIDADES.put(29, "veintinueve"); UNIDADES.put(30, "treinta");
        UNIDADES.put(31, "treinta y uno");
    }

    public static String numeroALetras(int numero) {
        if (numero < 1 || numero > 31) return "";
        return UNIDADES.getOrDefault(numero, String.valueOf(numero));
    }

    public static String mesALetras(LocalDate date) {
        if (date == null) return "";
        Month month = date.getMonth();
        return MESES[month.getValue() - 1];
    }

    public static String anioALetras(int year) {
        return String.valueOf(year);
    }
}

