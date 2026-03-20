export async function regenerarPdf(tramiteId) {
    const response = await fetch(`${API_URL}/${tramiteId}/regenerar-pdf`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
    });
    if (!response.ok) {
        const error = await response.text();
        throw new Error(error || 'No se pudo regenerar el PDF');
    }
    return response.json();
}
import { API_TRAMITES_URL } from '../config/api';

const API_URL = API_TRAMITES_URL;
const TRAMITES_CACHE_KEY = 'tramites_cache_v1';
const TRAMITES_CACHE_TTL_MS = 90 * 1000;

const puedeUsarStorage = () => typeof window !== 'undefined' && !!window.localStorage;

const leerCacheTramites = () => {
    if (!puedeUsarStorage()) return null;
    try {
        const raw = localStorage.getItem(TRAMITES_CACHE_KEY);
        if (!raw) return null;
        const parsed = JSON.parse(raw);
        if (!parsed || !Array.isArray(parsed.data) || typeof parsed.savedAt !== 'number') {
            return null;
        }
        return parsed;
    } catch {
        return null;
    }
};

const guardarCacheTramites = (data) => {
    if (!puedeUsarStorage() || !Array.isArray(data)) return;
    try {
        localStorage.setItem(TRAMITES_CACHE_KEY, JSON.stringify({
            data,
            savedAt: Date.now(),
        }));
    } catch {
        // no-op
    }
};

export function limpiarCacheTramites() {
    if (!puedeUsarStorage()) return;
    try {
        localStorage.removeItem(TRAMITES_CACHE_KEY);
    } catch {
        // no-op
    }
}

export async function listarTramites({ forceRefresh = false, allowStaleOnError = true } = {}) {
    const cache = leerCacheTramites();
    const cacheVigente = cache && (Date.now() - cache.savedAt) <= TRAMITES_CACHE_TTL_MS;

    if (!forceRefresh && cacheVigente) {
        return cache.data;
    }

    try {
        const response = await fetch(API_URL);
        if (!response.ok) {
            throw new Error('No se pudo cargar la lista de trámites');
        }
        const data = await response.json();
        guardarCacheTramites(data);
        return data;
    } catch (error) {
        if (allowStaleOnError && cache && Array.isArray(cache.data)) {
            return cache.data;
        }
        throw error;
    }
}

export async function crearTramite(payload) {
    const response = await fetch(API_URL, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
    });
    if (!response.ok) throw new Error('No se pudo crear el trámite');
    limpiarCacheTramites();
    return response.json();
}

export async function radicarCertificadoResidencia(solicitud) {
    const response = await fetch(`${API_URL}/solicitud-residencia`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(solicitud),
    });
    if (!response.ok) {
        const error = await response.text();
        throw new Error(error || 'No se pudo radicar la solicitud');
    }
    limpiarCacheTramites();
    return response.json();
}

export async function verificarCertificado(numeroRadicado, factorTipo, factorValor) {
    const criterio = encodeURIComponent((numeroRadicado || '').trim());
    const tipoParam = encodeURIComponent((factorTipo || '').trim());
    const valorParam = encodeURIComponent((factorValor || '').trim());
    const response = await fetch(`${API_URL}/verificacion/${criterio}?factorTipo=${tipoParam}&factorValor=${valorParam}`);
    if (!response.ok) {
        const error = await response.text();
        throw new Error(error || 'No se pudo verificar el certificado');
    }
    return response.json();
}

export async function consultarSolicitudesResueltas(numeroDocumento) {
    const response = await fetch(`${API_URL}/verificacion/resueltas/${encodeURIComponent(numeroDocumento)}`);
    if (!response.ok) {
        const error = await response.text();
        throw new Error(error || 'No se pudo consultar solicitudes resueltas');
    }
    return response.json();
}

export async function descargarCertificadoGenerado(tramiteId) {
    const response = await fetch(`${API_URL}/${tramiteId}/documento-generado?accion=ver`);
    if (!response.ok) {
        const error = await response.text();
        throw new Error(error || 'No se pudo descargar el certificado');
    }
    const blob = await response.blob();
    const url = window.URL.createObjectURL(blob);
    window.open(url, '_blank', 'noopener,noreferrer');
}

export async function enviarAVerificacion(tramiteId, verificacion) {
    const response = await fetch(`${API_URL}/${tramiteId}/verificacion`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(verificacion),
    });
    if (!response.ok) {
        const error = await response.text();
        throw new Error(error || 'No se pudo enviar a verificación');
    }
    limpiarCacheTramites();
    return response.json();
}

export async function firmarDocumento(tramiteId, firma) {
    const response = await fetch(`${API_URL}/${tramiteId}/firma-alcalde`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(firma),
    });
    if (!response.ok) throw new Error('No se pudo firmar el documento');
    limpiarCacheTramites();
    return response.json();
}
