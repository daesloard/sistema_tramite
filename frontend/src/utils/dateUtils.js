import { formatearFechaHora as formatearFechaHoraUtil } from './dateFormat';

export const formatearFechaHora = (valor) => formatearFechaHoraUtil(valor, { fallback: 'Sin fecha' });

export const normalizarFecha = (valor) => {
    if (!valor) return null;
    const fecha = new Date(valor);
    if (Number.isNaN(fecha.getTime())) return null;
    return new Date(fecha.getFullYear(), fecha.getMonth(), fecha.getDate());
};

export const esDiaHabil = (fecha) => {
    const dia = fecha.getDay();
    return dia !== 0 && dia !== 6;
};

export const calcularDiasHabilesEntre = (inicio, fin) => {
    if (!inicio || !fin) return 0;
    if (inicio.getTime() === fin.getTime()) return 0;

    const avanzar = inicio < fin;
    let cursor = new Date(inicio);
    let total = 0;

    while ((avanzar && cursor < fin) || (!avanzar && cursor > fin)) {
        cursor.setDate(cursor.getDate() + (avanzar ? 1 : -1));
        if (esDiaHabil(cursor)) {
            total += avanzar ? 1 : -1;
        }
    }

    return total;
};

export const obtenerTextoDiasHabilesRestantes = (fechaVencimiento) => {
    const vencimiento = normalizarFecha(fechaVencimiento);
    if (!vencimiento) return null;

    const hoy = normalizarFecha(new Date());
    const dias = calcularDiasHabilesEntre(hoy, vencimiento);

    if (dias > 0) {
        return `⏳ Faltan ${dias} día(s) hábil(es)`;
    }
    if (dias === 0) {
        return '⏳ Vence hoy';
    }
    return `⚠️ Vencido hace ${Math.abs(dias)} día(s) hábil(es)`;
};
