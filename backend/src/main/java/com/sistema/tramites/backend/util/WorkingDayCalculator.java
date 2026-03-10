package com.sistema.tramites.backend.util;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.DayOfWeek;
import java.util.HashSet;
import java.util.Set;

@Service
public class WorkingDayCalculator {

    private static final Set<LocalDate> FESTIVOS_COLOMBIA = new HashSet<>();
    
    static {
        // Festivos oficiales de Colombia 2026 (18 días - festivos.com.co)
        FESTIVOS_COLOMBIA.add(LocalDate.of(2026, 1, 1));   // 1 enero: Año Nuevo
        FESTIVOS_COLOMBIA.add(LocalDate.of(2026, 1, 12));  // 12 enero: Reyes Magos
        FESTIVOS_COLOMBIA.add(LocalDate.of(2026, 3, 23));  // 23 marzo: Día de San José
        FESTIVOS_COLOMBIA.add(LocalDate.of(2026, 4, 2));   // 2 abril: Jueves Santo
        FESTIVOS_COLOMBIA.add(LocalDate.of(2026, 4, 3));   // 3 abril: Viernes Santo
        FESTIVOS_COLOMBIA.add(LocalDate.of(2026, 5, 1));   // 1 mayo: Día del Trabajo
        FESTIVOS_COLOMBIA.add(LocalDate.of(2026, 5, 18));  // 18 mayo: Ascensión de Jesús
        FESTIVOS_COLOMBIA.add(LocalDate.of(2026, 6, 8));   // 8 junio: Corpus Christi
        FESTIVOS_COLOMBIA.add(LocalDate.of(2026, 6, 15));  // 15 junio: Sagrado Corazón de Jesús
        FESTIVOS_COLOMBIA.add(LocalDate.of(2026, 6, 29));  // 29 junio: San Pedro y San Pablo
        FESTIVOS_COLOMBIA.add(LocalDate.of(2026, 7, 20));  // 20 julio: Día de la Independencia
        FESTIVOS_COLOMBIA.add(LocalDate.of(2026, 8, 7));   // 7 agosto: Batalla de Boyacá
        FESTIVOS_COLOMBIA.add(LocalDate.of(2026, 8, 17));  // 17 agosto: Asunción de la Virgen
        FESTIVOS_COLOMBIA.add(LocalDate.of(2026, 10, 12)); // 12 octubre: Día de la Raza
        FESTIVOS_COLOMBIA.add(LocalDate.of(2026, 11, 2));  // 2 noviembre: Todos los Santos
        FESTIVOS_COLOMBIA.add(LocalDate.of(2026, 11, 16)); // 16 noviembre: Independencia de Cartagena
        FESTIVOS_COLOMBIA.add(LocalDate.of(2026, 12, 8));  // 8 diciembre: Inmaculada Concepción
        FESTIVOS_COLOMBIA.add(LocalDate.of(2026, 12, 25)); // 25 diciembre: Navidad
        
        // Festivos oficiales de Colombia 2027 (18 días - festivos.com.co)
        FESTIVOS_COLOMBIA.add(LocalDate.of(2027, 1, 1));   // 1 enero: Año Nuevo
        FESTIVOS_COLOMBIA.add(LocalDate.of(2027, 1, 11));  // 11 enero: Reyes Magos
        FESTIVOS_COLOMBIA.add(LocalDate.of(2027, 3, 22));  // 22 marzo: Día de San José
        FESTIVOS_COLOMBIA.add(LocalDate.of(2027, 3, 25));  // 25 marzo: Jueves Santo
        FESTIVOS_COLOMBIA.add(LocalDate.of(2027, 3, 26));  // 26 marzo: Viernes Santo
        FESTIVOS_COLOMBIA.add(LocalDate.of(2027, 5, 1));   // 1 mayo: Día del Trabajo
        FESTIVOS_COLOMBIA.add(LocalDate.of(2027, 5, 10));  // 10 mayo: Ascensión de Jesús
        FESTIVOS_COLOMBIA.add(LocalDate.of(2027, 5, 31));  // 31 mayo: Corpus Christi
        FESTIVOS_COLOMBIA.add(LocalDate.of(2027, 6, 7));   // 7 junio: Sagrado Corazón de Jesús
        FESTIVOS_COLOMBIA.add(LocalDate.of(2027, 7, 5));   // 5 julio: San Pedro y San Pablo
        FESTIVOS_COLOMBIA.add(LocalDate.of(2027, 7, 20));  // 20 julio: Día de la Independencia
        FESTIVOS_COLOMBIA.add(LocalDate.of(2027, 8, 7));   // 7 agosto: Batalla de Boyacá
        FESTIVOS_COLOMBIA.add(LocalDate.of(2027, 8, 16));  // 16 agosto: Asunción de la Virgen
        FESTIVOS_COLOMBIA.add(LocalDate.of(2027, 10, 18)); // 18 octubre: Día de la Raza
        FESTIVOS_COLOMBIA.add(LocalDate.of(2027, 11, 1));  // 1 noviembre: Todos los Santos
        FESTIVOS_COLOMBIA.add(LocalDate.of(2027, 11, 15)); // 15 noviembre: Independencia de Cartagena
        FESTIVOS_COLOMBIA.add(LocalDate.of(2027, 12, 8));  // 8 diciembre: Inmaculada Concepción
        FESTIVOS_COLOMBIA.add(LocalDate.of(2027, 12, 25)); // 25 diciembre: Navidad
    }

    /**
     * Calcula la fecha de vencimiento agregando X días hábiles a una fecha inicial
     * @param fechaInicio fecha de inicio
     * @param diasHabiles cantidad de días hábiles a agregar
     * @return fecha de vencimiento
     */
    public LocalDate calcularFechaVencimiento(LocalDate fechaInicio, int diasHabiles) {
        LocalDate fecha = fechaInicio;
        int contadorDias = 0;
        
        while (contadorDias < diasHabiles) {
            fecha = fecha.plusDays(1);
            if (esHabil(fecha)) {
                contadorDias++;
            }
        }
        
        return fecha;
    }

    /**
     * Verifica si una fecha es hábil (lunes a viernes y no festivo)
     * @param fecha a verificar
     * @return true si es hábil
     */
    public boolean esHabil(LocalDate fecha) {
        DayOfWeek diaSemana = fecha.getDayOfWeek();
        boolean esEntresemana = diaSemana != DayOfWeek.SATURDAY && diaSemana != DayOfWeek.SUNDAY;
        boolean noEsFestivo = !FESTIVOS_COLOMBIA.contains(fecha);
        return esEntresemana && noEsFestivo;
    }

    /**
     * Calcula la fecha de vigencia (6 meses desde hoy)
     * @return fecha de vigencia
     */
    public LocalDate calcularFechaVigencia() {
        return calcularFechaVigencia(LocalDate.now());
    }

    /**
     * Calcula la fecha de vigencia como 6 meses calendario menos 1 día.
     * Ejemplo: 25-feb -> 24-ago.
     * @param fechaInicio fecha de inicio de vigencia
     * @return fecha fin de vigencia
     */
    public LocalDate calcularFechaVigencia(LocalDate fechaInicio) {
        if (fechaInicio == null) {
            fechaInicio = LocalDate.now();
        }
        return fechaInicio.plusMonths(6).minusDays(1);
    }
}
