export const filtrarCertificadosGenerados = (items, filtros = {}) => {
  const filtroRadicado = (filtros.radicado || '').toLowerCase().trim();
  const filtroNombre = (filtros.nombre || '').toLowerCase().trim();
  const filtroTipo = filtros.tipo || 'todos';

  return (items || [])
    .filter((item) => item.estado === 'FINALIZADO' || item.estado === 'RECHAZADO')
    .filter((item) => {
      const matchRadicado = (item.numeroRadicado || '').toLowerCase().includes(filtroRadicado);
      const matchNombre = (item.nombreSolicitante || '').toLowerCase().includes(filtroNombre);
      const matchTipo = filtroTipo === 'todos'
        || (filtroTipo === 'positiva' && item.estado === 'FINALIZADO')
        || (filtroTipo === 'negativa' && item.estado === 'RECHAZADO');

      return matchRadicado && matchNombre && matchTipo;
    });
};
