export const formatearFecha = (valor, opciones = {}) => {
  const fallback = opciones.fallback ?? 'Sin fecha';
  if (!valor) return fallback;

  const fecha = new Date(valor);
  if (Number.isNaN(fecha.getTime())) return fallback;

  return fecha.toLocaleDateString('es-CO');
};

export const formatearFechaHora = (valor, opciones = {}) => {
  const fallback = opciones.fallback ?? 'Sin fecha';
  const incluirSegundos = opciones.incluirSegundos ?? false;

  if (!valor) return fallback;

  const fecha = new Date(valor);
  if (Number.isNaN(fecha.getTime())) return fallback;

  const esSoloFecha = typeof valor === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(valor);
  if (esSoloFecha) {
    return fecha.toLocaleDateString('es-CO');
  }

  const opcionesFecha = {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  };

  if (incluirSegundos) {
    opcionesFecha.second = '2-digit';
  }

  return fecha.toLocaleString('es-CO', opcionesFecha);
};
