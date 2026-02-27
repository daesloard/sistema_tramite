import { API_TRAMITES_URL } from '../config/api';

const API_URL = API_TRAMITES_URL;

export async function listarTramites() {
  const response = await fetch(API_URL);
  if (!response.ok) {
    throw new Error('No se pudo cargar la lista de trámites');
  }
  return response.json();
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
  return response.json();
}

// Verificar certificado
export async function verificarCertificado(numeroRadicado) {
  const criterio = encodeURIComponent((numeroRadicado || '').trim());
  const response = await fetch(`${API_URL}/verificacion/${criterio}`);
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
  const response = await fetch(`${API_URL}/${tramiteId}/documento-generado`);
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
    throw new Error('No se pudo enviar a verificación');
  }
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
  return response.json();
}
