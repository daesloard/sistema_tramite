import { API_NOTIFICACIONES_URL, API_ORIGIN, API_TRAMITES_URL } from '../config/api';

const API_URL = API_TRAMITES_URL;
const TRAMITES_CACHE_KEY = 'tramites_cache_v1';
const TRAMITES_CACHE_TTL_MS = 90 * 1000;

const construirUrlMetricaActuator = (nombreMetrica, tags = []) => {
  const url = new URL(`${API_ORIGIN}/actuator/metrics/${encodeURIComponent(nombreMetrica)}`);
  tags.forEach(([key, value]) => {
    if (!key || value == null) return;
    url.searchParams.append('tag', `${key}:${value}`);
  });
  return url.toString();
};

const leerAgregadoMetrica = (metrica, estadistico, estrategia = 'sum') => {
  const measurements = Array.isArray(metrica?.measurements) ? metrica.measurements : [];
  const objetivo = (estadistico || '').toUpperCase();
  const valores = measurements
    .filter((item) => (item?.statistic || '').toUpperCase() === objetivo)
    .map((item) => Number(item?.value))
    .filter((valor) => Number.isFinite(valor));

  if (valores.length === 0) return null;
  if (estrategia === 'max') return Math.max(...valores);
  if (estrategia === 'first') return valores[0];
  return valores.reduce((total, valor) => total + valor, 0);
};

const leerCuantilHttpDesdePrometheus = (contenido, cuantil) => {
  if (!contenido || !cuantil) return null;
  const regex = new RegExp(`^http_server_requests_seconds\\{[^\\n]*quantile="${cuantil}"[^\\n]*\\}\\s+([0-9.eE+-]+)$`, 'gm');
  const valores = [];
  let match = regex.exec(contenido);
  while (match) {
    const valor = Number(match[1]);
    if (Number.isFinite(valor)) {
      valores.push(valor);
    }
    match = regex.exec(contenido);
  }
  if (valores.length === 0) return null;
  return Math.max(...valores);
};

async function obtenerMetricaActuator(nombreMetrica, tags = []) {
  const response = await fetch(construirUrlMetricaActuator(nombreMetrica, tags));
  if (response.status === 404) {
    return null;
  }
  if (!response.ok) {
    const detalle = await response.text();
    throw new Error(detalle || `No se pudo cargar la métrica ${nombreMetrica}`);
  }
  return response.json();
}

async function obtenerPrometheusRaw() {
  const response = await fetch(`${API_ORIGIN}/actuator/prometheus`);
  if (response.status === 404) {
    return '';
  }
  if (!response.ok) {
    throw new Error('No se pudo cargar Prometheus');
  }
  return response.text();
}

async function actuatorDisponible() {
  try {
    const response = await fetch(`${API_ORIGIN}/actuator/health`);
    return response.ok;
  } catch {
    return false;
  }
}

async function obtenerCatalogoMetricasActuator() {
  const response = await fetch(`${API_ORIGIN}/actuator/metrics`);
  if (response.status === 404) {
    return null;
  }
  if (!response.ok) {
    throw new Error('No se pudo cargar el catalogo de metricas');
  }
  return response.json();
}

const catalogoTieneMetrica = (catalogo, nombreMetrica) => (
  Array.isArray(catalogo?.names) && catalogo.names.includes(nombreMetrica)
);

const metricaTieneTag = (metrica, tagNombre, tagValor) => {
  const tags = Array.isArray(metrica?.availableTags) ? metrica.availableTags : [];
  const tag = tags.find((item) => item?.tag === tagNombre);
  return Array.isArray(tag?.values) && tag.values.includes(tagValor);
};

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
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(payload),
  });

  if (!response.ok) {
    throw new Error('No se pudo crear el trámite');
  }
  limpiarCacheTramites();
  return response.json();
}

// Nuevo endpoint para radicación de Certificado de Residencia
export async function radicarCertificadoResidencia(solicitud) {
  const response = await fetch(`${API_URL}/solicitud-residencia`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(solicitud),
  });

  if (!response.ok) {
    const error = await response.text();
    throw new Error(error || 'No se pudo radicar la solicitud');
  }
  limpiarCacheTramites();
  return response.json();
}

// Verificar certificado
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

// Enviar a verificación
export async function enviarAVerificacion(tramiteId, verificacion) {
  const response = await fetch(`${API_URL}/${tramiteId}/verificacion`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(verificacion),
  });

  if (!response.ok) {
    const error = await response.text();
    throw new Error(error || 'No se pudo enviar a verificación');
  }
  limpiarCacheTramites();
  return response.json();
}

// Firma del alcalde
export async function firmarDocumento(tramiteId, firma) {
  const response = await fetch(`${API_URL}/${tramiteId}/firma-alcalde`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(firma),
  });

  if (!response.ok) {
    throw new Error('No se pudo firmar el documento');
  }
  limpiarCacheTramites();
  return response.json();
}

export async function obtenerWebPushPublicKey() {
  const response = await fetch(`${API_NOTIFICACIONES_URL}/webpush/public-key`);
  if (!response.ok) {
    throw new Error('No se pudo obtener la configuración Web Push');
  }
  return response.json();
}

export async function registrarWebPushSuscripcion({ username, subscription }) {
  const response = await fetch(`${API_NOTIFICACIONES_URL}/webpush/subscribe`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-Username': username,
    },
    body: JSON.stringify(subscription),
  });

  if (!response.ok) {
    throw new Error('No se pudo registrar la suscripción Web Push');
  }
  return response.json();
}

export async function desactivarWebPushSuscripcion({ username, endpoint }) {
  const response = await fetch(`${API_NOTIFICACIONES_URL}/webpush/subscribe`, {
    method: 'DELETE',
    headers: {
      'Content-Type': 'application/json',
      'X-Username': username,
    },
    body: JSON.stringify({ endpoint }),
  });

  if (!response.ok) {
    throw new Error('No se pudo desactivar la suscripción Web Push');
  }
  return response.json();
}

export async function obtenerMetricasOperativasAdmin() {
  const disponible = await actuatorDisponible();
  if (!disponible) {
    return {
      available: false,
      capturedAt: new Date().toISOString(),
      postFirma: {},
      pdf: {},
      http: {},
    };
  }

  const catalogo = await obtenerCatalogoMetricasActuator().catch(() => null);
  if (!catalogo) {
    return {
      available: false,
      capturedAt: new Date().toISOString(),
      postFirma: {},
      pdf: {},
      http: {},
    };
  }

  const tieneHttp = catalogoTieneMetrica(catalogo, 'http.server.requests');
  const tienePostFirmaTotal = catalogoTieneMetrica(catalogo, 'tramites.postfirma.total');
  const tienePostFirmaDuration = catalogoTieneMetrica(catalogo, 'tramites.postfirma.duration');
  const tienePostFirmaEmailErrors = catalogoTieneMetrica(catalogo, 'tramites.postfirma.email.errors');
  const tienePdfTotal = catalogoTieneMetrica(catalogo, 'tramites.pdf.generation.total');
  const tienePdfDuration = catalogoTieneMetrica(catalogo, 'tramites.pdf.generation.duration');
  const tienePdfErrors = catalogoTieneMetrica(catalogo, 'tramites.pdf.generation.errors');

  const [
    metricaHttp,
    metricaPostFirmaTotal,
    metricaPostFirmaDuration,
    metricaPostFirmaEmailErrors,
    metricaPdfTotal,
    metricaPdfDuration,
    metricaPdfErrors,
    prometheusRaw,
  ] = await Promise.all([
    tieneHttp ? obtenerMetricaActuator('http.server.requests').catch(() => null) : Promise.resolve(null),
    tienePostFirmaTotal ? obtenerMetricaActuator('tramites.postfirma.total').catch(() => null) : Promise.resolve(null),
    tienePostFirmaDuration ? obtenerMetricaActuator('tramites.postfirma.duration').catch(() => null) : Promise.resolve(null),
    tienePostFirmaEmailErrors ? obtenerMetricaActuator('tramites.postfirma.email.errors').catch(() => null) : Promise.resolve(null),
    tienePdfTotal ? obtenerMetricaActuator('tramites.pdf.generation.total').catch(() => null) : Promise.resolve(null),
    tienePdfDuration ? obtenerMetricaActuator('tramites.pdf.generation.duration').catch(() => null) : Promise.resolve(null),
    tienePdfErrors ? obtenerMetricaActuator('tramites.pdf.generation.errors').catch(() => null) : Promise.resolve(null),
    obtenerPrometheusRaw().catch(() => ''),
  ]);

  const consultarOutcomeSuccessTotal = metricaTieneTag(metricaPostFirmaTotal, 'outcome', 'success');
  const consultarOutcomeExceptionTotal = metricaTieneTag(metricaPostFirmaTotal, 'outcome', 'exception');
  const consultarOutcomeSuccessDuration = metricaTieneTag(metricaPostFirmaDuration, 'outcome', 'success');
  const consultarPdfOutcomeSuccessTotal = metricaTieneTag(metricaPdfTotal, 'outcome', 'success');
  const consultarPdfOutcomeErrorTotal = metricaTieneTag(metricaPdfTotal, 'outcome', 'error');
  const consultarPdfEngineLibreoffice = metricaTieneTag(metricaPdfTotal, 'engine', 'libreoffice');
  const consultarPdfEngineDocx4j = metricaTieneTag(metricaPdfTotal, 'engine', 'docx4j');
  const consultarPdfOutcomeSuccessDuration = metricaTieneTag(metricaPdfDuration, 'outcome', 'success');

  const [
    metricaPostFirmaSuccess,
    metricaPostFirmaException,
    metricaPostFirmaDurationSuccess,
    metricaPdfSuccess,
    metricaPdfErrorTotal,
    metricaPdfEngineLibreoffice,
    metricaPdfEngineDocx4j,
    metricaPdfDurationSuccess,
  ] = await Promise.all([
    consultarOutcomeSuccessTotal
      ? obtenerMetricaActuator('tramites.postfirma.total', [['outcome', 'success']]).catch(() => null)
      : Promise.resolve(null),
    consultarOutcomeExceptionTotal
      ? obtenerMetricaActuator('tramites.postfirma.total', [['outcome', 'exception']]).catch(() => null)
      : Promise.resolve(null),
    consultarOutcomeSuccessDuration
      ? obtenerMetricaActuator('tramites.postfirma.duration', [['outcome', 'success']]).catch(() => null)
      : Promise.resolve(null),
    consultarPdfOutcomeSuccessTotal
      ? obtenerMetricaActuator('tramites.pdf.generation.total', [['outcome', 'success']]).catch(() => null)
      : Promise.resolve(null),
    consultarPdfOutcomeErrorTotal
      ? obtenerMetricaActuator('tramites.pdf.generation.total', [['outcome', 'error']]).catch(() => null)
      : Promise.resolve(null),
    consultarPdfEngineLibreoffice
      ? obtenerMetricaActuator('tramites.pdf.generation.total', [['engine', 'libreoffice']]).catch(() => null)
      : Promise.resolve(null),
    consultarPdfEngineDocx4j
      ? obtenerMetricaActuator('tramites.pdf.generation.total', [['engine', 'docx4j']]).catch(() => null)
      : Promise.resolve(null),
    consultarPdfOutcomeSuccessDuration
      ? obtenerMetricaActuator('tramites.pdf.generation.duration', [['outcome', 'success']]).catch(() => null)
      : Promise.resolve(null),
  ]);

  const duracionOkCount = leerAgregadoMetrica(metricaPostFirmaDurationSuccess, 'COUNT', 'sum');
  const duracionOkTotalTime = leerAgregadoMetrica(metricaPostFirmaDurationSuccess, 'TOTAL_TIME', 'sum');
  const duracionPdfCount = leerAgregadoMetrica(metricaPdfDurationSuccess || metricaPdfDuration, 'COUNT', 'sum');
  const duracionPdfTotalTime = leerAgregadoMetrica(metricaPdfDurationSuccess || metricaPdfDuration, 'TOTAL_TIME', 'sum');

  return {
    available: true,
    capturedAt: new Date().toISOString(),
    postFirma: {
      total: leerAgregadoMetrica(metricaPostFirmaTotal, 'COUNT', 'sum'),
      success: leerAgregadoMetrica(metricaPostFirmaSuccess, 'COUNT', 'sum'),
      exception: leerAgregadoMetrica(metricaPostFirmaException, 'COUNT', 'sum'),
      emailErrors: leerAgregadoMetrica(metricaPostFirmaEmailErrors, 'COUNT', 'sum'),
      successAvgSeconds: (
        Number.isFinite(duracionOkCount)
        && duracionOkCount > 0
        && Number.isFinite(duracionOkTotalTime)
      )
        ? (duracionOkTotalTime / duracionOkCount)
        : null,
      successMaxSeconds: leerAgregadoMetrica(metricaPostFirmaDurationSuccess, 'MAX', 'max'),
    },
    pdf: {
      total: leerAgregadoMetrica(metricaPdfTotal, 'COUNT', 'sum'),
      success: leerAgregadoMetrica(metricaPdfSuccess, 'COUNT', 'sum'),
      errorTotal: leerAgregadoMetrica(metricaPdfErrorTotal, 'COUNT', 'sum'),
      errors: leerAgregadoMetrica(metricaPdfErrors, 'COUNT', 'sum'),
      engineLibreoffice: leerAgregadoMetrica(metricaPdfEngineLibreoffice, 'COUNT', 'sum'),
      engineDocx4j: leerAgregadoMetrica(metricaPdfEngineDocx4j, 'COUNT', 'sum'),
      avgSeconds: (
        Number.isFinite(duracionPdfCount)
        && duracionPdfCount > 0
        && Number.isFinite(duracionPdfTotalTime)
      )
        ? (duracionPdfTotalTime / duracionPdfCount)
        : null,
      maxSeconds: leerAgregadoMetrica(metricaPdfDurationSuccess || metricaPdfDuration, 'MAX', 'max'),
    },
    http: {
      count: leerAgregadoMetrica(metricaHttp, 'COUNT', 'sum'),
      totalTimeSeconds: leerAgregadoMetrica(metricaHttp, 'TOTAL_TIME', 'sum'),
      maxSeconds: leerAgregadoMetrica(metricaHttp, 'MAX', 'max'),
      p95Seconds: leerCuantilHttpDesdePrometheus(prometheusRaw, '0.95'),
      p99Seconds: leerCuantilHttpDesdePrometheus(prometheusRaw, '0.99'),
    },
  };
}
