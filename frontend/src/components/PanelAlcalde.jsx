import { useEffect, useMemo, useState } from 'react';
import { API_TRAMITES_URL } from '../config/api';

const FILTROS = [
  { key: 'pendientes', label: 'Pendientes', titulo: '📑 Certificados Pendientes de Firma', estado: 'EN_FIRMA' },
  { key: 'aprobadas', label: 'Aprobadas', titulo: '✅ Solicitudes Aprobadas', estado: 'FINALIZADO' },
  { key: 'negadas', label: 'Negadas', titulo: '❌ Solicitudes Negadas', estado: 'RECHAZADO' },
];

const styles = {
  panelCargando: { textAlign: 'center', padding: '40px', fontSize: '16px', color: '#7f8c8d' },
  contenedor: {
    maxWidth: '1600px',
    margin: '0 auto',
    padding: 'clamp(0.75rem, 3.5vw, 1.25rem)',
    background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
    minHeight: '100vh',
  },
  encabezado: {
    background: '#fff',
    padding: 'clamp(0.9rem, 4vw, 1.5rem)',
    borderRadius: '12px',
    boxShadow: '0 4px 12px rgba(0, 0, 0, 0.12)',
    marginBottom: '20px',
    borderLeft: '5px solid #667eea',
  },
  h2: { margin: '0 0 8px 0', color: '#2c3e50', fontSize: 'clamp(1.15rem, 5vw, 1.75rem)' },
  subtitulo: { margin: 0, color: '#7f8c8d', fontSize: '14px' },
  filtros: { marginTop: '14px', display: 'flex', gap: '8px', flexWrap: 'nowrap', overflowX: 'auto', overflowY: 'hidden', WebkitOverflowScrolling: 'touch' },
  btnFiltro: {
    border: '1px solid #cfd8dc',
    background: '#fff',
    color: '#2c3e50',
    padding: '6px 10px',
    borderRadius: '8px',
    cursor: 'pointer',
    fontWeight: 600,
    width: 'auto',
    display: 'inline-flex',
    alignItems: 'center',
    justifyContent: 'center',
    flex: '0 0 auto',
    whiteSpace: 'nowrap',
    fontSize: '0.85rem',
  },
  btnFiltroActivo: { background: '#667eea', color: '#fff', borderColor: '#667eea' },
  panelError: {
    background: '#ffe0e0',
    border: '1px solid #ff6b6b',
    color: '#c92a2a',
    padding: '16px',
    borderRadius: '8px',
    marginBottom: '20px',
  },
  panelContenido: { display: 'grid', gap: '14px' },
  panelContenidoDesktop: { gridTemplateColumns: '420px minmax(0, 1fr)' },
  panelContenidoMobile: { gridTemplateColumns: '1fr' },
  panelLista: {
    background: '#fff',
    padding: '14px',
    borderRadius: '12px',
    boxShadow: '0 4px 12px rgba(0, 0, 0, 0.1)',
    height: 'fit-content',
    position: 'static',
    borderLeft: '4px solid #667eea',
  },
  panelListaDesktop: { position: 'sticky', top: '20px' },
  h3Lista: { margin: '0 0 15px 0', fontSize: '16px', color: '#2c3e50' },
  busquedaWrap: { display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '10px', flexWrap: 'wrap' },
  busquedaInput: { width: 'min(380px, 100%)', border: '1px solid #cfd8dc', borderRadius: '6px', padding: '8px 10px', fontSize: '13px', boxSizing: 'border-box' },
  busquedaMeta: { margin: 0, fontSize: '12px', color: '#6b7280' },
  busquedaBtn: { padding: '0.35rem 0.75rem', background: '#4f46e5', color: '#fff', border: 'none', borderRadius: '6px', cursor: 'pointer', fontWeight: 600, fontSize: '0.78rem', width: 'auto', whiteSpace: 'nowrap' },
  listaVacia: { textAlign: 'center', padding: '30px 10px', color: '#27ae60' },
  subtexto: { color: '#95a5a6', fontSize: '12px', margin: '8px 0 0 0' },
  listaSolicitudes: { display: 'flex', flexDirection: 'column', gap: '10px', overflowY: 'auto', overflowX: 'hidden', WebkitOverflowScrolling: 'touch' },
  listaSolicitudesDesktop: { maxHeight: 'calc(100vh - 300px)' },
  listaSolicitudesMobile: { maxHeight: '50vh' },
  solicitudItem: {
    padding: '15px',
    border: '2px solid #ecf0f1',
    borderRadius: '8px',
    cursor: 'pointer',
    background: '#f9fafb',
    borderLeft: '4px solid #667eea',
  },
  solicitudActiva: { borderColor: '#667eea', background: '#f0f4ff', boxShadow: '0 0 0 3px rgba(102, 126, 234, 0.15)' },
  fila: { display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '10px' },
  radicado: { fontWeight: 'bold', color: '#667eea', fontFamily: 'monospace', fontSize: '13px' },
  badge: { fontSize: '11px', padding: '4px 8px', borderRadius: '12px', background: '#e3f2fd', color: '#667eea', fontWeight: 600 },
  itemP: { margin: '4px 0', fontSize: '13px' },
  itemStrong: { color: '#2c3e50', fontSize: '14px' },
  panelDetalle: { background: '#fff', borderRadius: '12px', boxShadow: '0 4px 12px rgba(0, 0, 0, 0.1)', overflow: 'hidden' },
  detalleHeader: {
    background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
    color: '#fff',
    padding: '20px',
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  detalleH3: { margin: 0, fontSize: '18px' },
  btnCerrar: {
    background: 'rgba(255, 255, 255, 0.2)',
    border: 'none',
    color: '#fff',
    width: '36px',
    height: '36px',
    borderRadius: '50%',
    cursor: 'pointer',
    fontSize: '20px',
  },
  contenido: { padding: 'clamp(0.9rem, 3.5vw, 1.5rem)', maxHeight: 'calc(100vh - 220px)', overflowY: 'auto' },
  seccion: { marginBottom: '22px', paddingBottom: '18px', borderBottom: '1px solid #ecf0f1' },
  h4: { margin: '0 0 15px 0', fontSize: '15px', color: '#2c3e50' },
  infoGrid: { display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: '12px' },
  infoItem: { background: '#f9fafb', padding: '12px', borderRadius: '6px', borderLeft: '3px solid #667eea' },
  label: { display: 'block', fontSize: '12px', color: '#7f8c8d', textTransform: 'uppercase', fontWeight: 600, marginBottom: '6px' },
  infoP: { margin: 0, fontSize: '14px', color: '#2c3e50', fontWeight: 500 },
  vistaWrap: { border: '2px solid #ecf0f1', borderRadius: '8px', padding: '16px', background: '#fff' },
  previewFrame: { width: '100%', minHeight: 'min(70vh, 800px)', border: '1px solid #e5e7eb', borderRadius: '6px', background: '#fff' },
  previewTexto: {
    whiteSpace: 'pre-wrap',
    fontFamily: 'Georgia, serif',
    fontSize: '13px',
    lineHeight: 1.7,
    color: '#1f2937',
    background: '#fcfcfc',
    border: '1px solid #e5e7eb',
    borderRadius: '6px',
    padding: '14px',
    margin: 0,
  },
  inputFirma: {
    width: '100%',
    padding: '12px',
    border: '2px solid #ecf0f1',
    borderRadius: '6px',
    fontSize: '14px',
    fontFamily: 'monospace',
    letterSpacing: '3px',
    boxSizing: 'border-box',
  },
  accionesFirma: { display: 'flex', justifyContent: 'center', marginTop: '20px' },
  btnFirmar: {
    padding: '10px 18px',
    background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
    color: '#fff',
    border: 'none',
    borderRadius: '6px',
    fontSize: '14px',
    fontWeight: 600,
    cursor: 'pointer',
    minWidth: '260px',
    width: 'auto',
  },
  btnFirmarDisabled: { background: '#bdc3c7', cursor: 'not-allowed', opacity: 0.6 },
  infoLegal: { background: '#fff3cd', borderLeft: '4px solid #ffc107', padding: '12px', borderRadius: '4px', marginTop: '20px' },
  infoLegalP: { margin: 0, fontSize: '12px', color: '#856404', lineHeight: 1.5 },
  certificadosSeccion: { background: '#fff', padding: '14px', borderRadius: '12px', boxShadow: '0 4px 12px rgba(0,0,0,0.1)', marginBottom: '14px' },
  filtrosCert: { display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: '8px', marginBottom: '10px' },
  inputFiltro: { width: '100%', border: '1px solid #cfd8dc', borderRadius: '6px', padding: '8px 10px', fontSize: '13px', boxSizing: 'border-box' },
  listaCertificados: { display: 'grid', gap: '8px', maxHeight: '220px', overflowY: 'auto' },
  certItem: { border: '1px solid #e5e7eb', borderRadius: '8px', padding: '8px 10px', background: '#f9fafb' },
  certMeta: { margin: '2px 0', fontSize: '12px', color: '#4b5563' },
  seccionHeader: { display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: '0.8rem', marginBottom: '10px', flexWrap: 'wrap' },
  btnToggleSeccion: { padding: '0.35rem 0.75rem', background: '#4f46e5', color: '#fff', border: 'none', borderRadius: '6px', cursor: 'pointer', fontWeight: 600, fontSize: '0.78rem', width: 'auto', whiteSpace: 'nowrap' },
  avisoOverlay: {
    position: 'fixed',
    inset: 0,
    background: 'rgba(17, 24, 39, 0.35)',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    padding: '16px',
    zIndex: 120,
  },
  panelAviso: { width: 'min(92vw, 520px)', padding: '14px 16px', borderRadius: '10px', border: '1px solid transparent', boxShadow: '0 10px 30px rgba(0,0,0,0.22)' },
  panelAvisoTexto: { margin: 0, fontSize: '14px', fontWeight: 600, lineHeight: 1.45 },
  panelAvisoAcciones: { marginTop: '12px', display: 'flex', justifyContent: 'flex-end' },
  panelAvisoCerrar: { border: 'none', background: '#1f2937', color: '#fff', borderRadius: '6px', cursor: 'pointer', fontSize: '12px', fontWeight: 700, lineHeight: 1, padding: '8px 10px' },
  panelAvisoError: { background: '#ffebee', borderColor: '#ef5350', color: '#b71c1c' },
  panelAvisoExito: { background: '#e8f5e9', borderColor: '#81c784', color: '#1b5e20' },
  panelAvisoInfo: { background: '#e3f2fd', borderColor: '#64b5f6', color: '#0d47a1' },
  panelAvisoWarning: { background: '#fff4e5', borderColor: '#f59e0b', color: '#92400e' },
};

const getEstadoBadge = (estado) => {
  if (estado === 'EN_FIRMA') return '🔖 Para Firmar';
  if (estado === 'FINALIZADO') return '✅ Aprobada';
  return '❌ Negada';
};

const formatearFecha = (valor) => {
  if (!valor) return 'Sin fecha';
  const fecha = new Date(valor);
  if (Number.isNaN(fecha.getTime())) return 'Sin fecha';
  const esSoloFecha = typeof valor === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(valor);
  return esSoloFecha
    ? fecha.toLocaleDateString('es-CO')
    : fecha.toLocaleString('es-CO', {
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
      });
};

export default function PanelAlcalde() {
  const [solicitudes, setSolicitudes] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [selectedSolicitud, setSelectedSolicitud] = useState(null);
  const [firmaDigital, setFirmaDigital] = useState('');
  const [filtroVista, setFiltroVista] = useState('pendientes');
  const [busquedaSolicitudes, setBusquedaSolicitudes] = useState('');
  const [vistaPrevia, setVistaPrevia] = useState('');
  const [vistaPreviaHtml, setVistaPreviaHtml] = useState('');
  const [vistaPreviaPdf, setVistaPreviaPdf] = useState('');
  const [loadingVistaPrevia, setLoadingVistaPrevia] = useState(false);
  const [procesando, setProcesando] = useState(false);
  const [isMobile, setIsMobile] = useState(typeof window !== 'undefined' ? window.innerWidth < 992 : false);
  const [filtroCertRadicado, setFiltroCertRadicado] = useState('');
  const [filtroCertNombre, setFiltroCertNombre] = useState('');
  const [filtroCertTipo, setFiltroCertTipo] = useState('todos');
  const [certificadosExpandido, setCertificadosExpandido] = useState(false);
  const [avisoPanel, setAvisoPanel] = useState(null);

  const mostrarAvisoPanel = (tipo, mensaje) => {
    setAvisoPanel({ tipo, mensaje });
  };

  const obtenerEstiloAvisoPanel = (tipo) => {
    if (tipo === 'success') return styles.panelAvisoExito;
    if (tipo === 'warning') return styles.panelAvisoWarning;
    if (tipo === 'info') return styles.panelAvisoInfo;
    return styles.panelAvisoError;
  };

  useEffect(() => {
    cargarSolicitudes();
  }, []);

  useEffect(() => {
    const onResize = () => setIsMobile(window.innerWidth < 992);
    window.addEventListener('resize', onResize);
    return () => window.removeEventListener('resize', onResize);
  }, []);

  useEffect(() => {
    if (selectedSolicitud?.id) {
      cargarVistaPrevia(selectedSolicitud.id);
      return;
    }
    setVistaPrevia('');
    setVistaPreviaHtml('');
    setVistaPreviaPdf('');
  }, [selectedSolicitud]);

  const filtroActual = useMemo(() => FILTROS.find((f) => f.key === filtroVista) || FILTROS[0], [filtroVista]);

  const solicitudesFiltradas = useMemo(
    () => solicitudes.filter((s) => s.estado === filtroActual.estado),
    [solicitudes, filtroActual]
  );
  const textoBusquedaSolicitudes = busquedaSolicitudes.trim().toLowerCase();
  const solicitudesFiltradasBusqueda = useMemo(() => {
    if (!textoBusquedaSolicitudes) return solicitudesFiltradas;
    return solicitudesFiltradas.filter((solicitud) => {
      const campos = [
        solicitud?.numeroRadicado,
        solicitud?.nombreSolicitante,
        solicitud?.numeroDocumento,
        solicitud?.estado,
        solicitud?.tipoTramite,
        solicitud?.tipo_certificado,
      ];
      return campos.some((valor) => (valor || '').toString().toLowerCase().includes(textoBusquedaSolicitudes));
    });
  }, [solicitudesFiltradas, textoBusquedaSolicitudes]);

  const certificadosGeneradosFiltrados = useMemo(() => {
    const base = solicitudes.filter((s) => s.estado === 'FINALIZADO' || s.estado === 'RECHAZADO');
    return base.filter((s) => {
      const matchRadicado = (s.numeroRadicado || '').toLowerCase().includes(filtroCertRadicado.toLowerCase().trim());
      const matchNombre = (s.nombreSolicitante || '').toLowerCase().includes(filtroCertNombre.toLowerCase().trim());
      const matchTipo = filtroCertTipo === 'todos'
        || (filtroCertTipo === 'positiva' && s.estado === 'FINALIZADO')
        || (filtroCertTipo === 'negativa' && s.estado === 'RECHAZADO');
      return matchRadicado && matchNombre && matchTipo;
    });
  }, [solicitudes, filtroCertRadicado, filtroCertNombre, filtroCertTipo]);

  const esPendienteFirma = selectedSolicitud?.estado === 'EN_FIRMA';

  const cargarSolicitudes = async () => {
    try {
      const response = await fetch(API_TRAMITES_URL);
      if (!response.ok) throw new Error('Error al cargar solicitudes');
      const data = await response.json();
      setSolicitudes(data.sort((a, b) => new Date(a.fechaRadicacion) - new Date(b.fechaRadicacion)));
      setError('');
    } catch (err) {
      setError(err.message || 'Error al cargar solicitudes');
    } finally {
      setLoading(false);
    }
  };

  const cargarVistaPrevia = async (tramiteId) => {
    setLoadingVistaPrevia(true);
    try {
      let headers = {};
      try {
        const userGuardado = localStorage.getItem('user');
        const user = userGuardado ? JSON.parse(userGuardado) : null;
        if (user?.username) {
          headers = { 'X-Username': user.username };
        }
      } catch {
        headers = {};
      }

      const response = await fetch(`${API_TRAMITES_URL}/${tramiteId}/vista-previa-documento?includePdf=false`, {
        headers,
      });
      if (!response.ok) throw new Error('No se pudo cargar la vista previa');
      const data = await response.json();
      setVistaPrevia(data.contenido || '');
      setVistaPreviaHtml(data.html || '');
      setVistaPreviaPdf(data.pdfBase64 || '');
    } catch {
      setVistaPrevia('No fue posible generar la vista previa del documento.');
      setVistaPreviaHtml('');
      setVistaPreviaPdf('');
    } finally {
      setLoadingVistaPrevia(false);
    }
  };

  const handleFirmar = async () => {
    if (!selectedSolicitud || !firmaDigital.trim()) {
      mostrarAvisoPanel('warning', 'Por favor, ingresa la contraseña para firmar');
      return;
    }

    setProcesando(true);
    try {
      const userGuardado = localStorage.getItem('user');
      const user = userGuardado ? JSON.parse(userGuardado) : null;

      const response = await fetch(`${API_TRAMITES_URL}/${selectedSolicitud.id}/firma-alcalde`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ firmaDigital, username: user?.username || 'alcalde' }),
      });

      if (!response.ok) {
        const msg = await response.text();
        throw new Error(msg || 'Error al firmar. Verifica la contraseña.');
      }

      mostrarAvisoPanel('success', 'Certificado firmado exitosamente. El PDF se está preparando y se enviará al solicitante en breve.');
      setSelectedSolicitud(null);
      setFirmaDigital('');
      await cargarSolicitudes();
    } catch (err) {
      mostrarAvisoPanel('error', `Error: ${err.message}`);
    } finally {
      setProcesando(false);
    }
  };

  if (loading) return <div style={styles.panelCargando}>Cargando solicitudes...</div>;

  return (
    <div style={styles.contenedor}>
      <div style={styles.encabezado}>
        <h2 style={styles.h2}>👨‍⚖️ Panel del Alcalde</h2>
        <p style={styles.subtitulo}>Firma los certificados de residencia aprobados</p>
        <div style={styles.filtros}>
          {FILTROS.map((filtro) => (
            <button
              key={filtro.key}
              style={{ ...styles.btnFiltro, ...(filtroVista === filtro.key ? styles.btnFiltroActivo : {}) }}
              onClick={() => {
                setFiltroVista(filtro.key);
                setSelectedSolicitud(null);
                setFirmaDigital('');
              }}
            >
              {filtro.label}
            </button>
          ))}
        </div>
      </div>

      {error ? (
        <div style={styles.panelError}>
          <p style={{ margin: 0 }}>⚠️ {error}</p>
        </div>
      ) : null}

      {avisoPanel ? (
        <div style={styles.avisoOverlay} onClick={() => setAvisoPanel(null)}>
          <div style={{ ...styles.panelAviso, ...obtenerEstiloAvisoPanel(avisoPanel.tipo) }} onClick={(e) => e.stopPropagation()}>
            <p style={styles.panelAvisoTexto}>{avisoPanel.mensaje}</p>
            <div style={styles.panelAvisoAcciones}>
              <button style={styles.panelAvisoCerrar} onClick={() => setAvisoPanel(null)} aria-label="Cerrar aviso">Entendido</button>
            </div>
          </div>
        </div>
      ) : null}

      <div style={styles.certificadosSeccion}>
        <div style={styles.seccionHeader}>
          <h3 style={{ ...styles.h3Lista, margin: 0 }}>📄 Certificados Generados ({certificadosGeneradosFiltrados.length})</h3>
          <button style={styles.btnToggleSeccion} onClick={() => setCertificadosExpandido((prev) => !prev)}>
            {certificadosExpandido ? 'Ocultar' : 'Mostrar'}
          </button>
        </div>

        {certificadosExpandido ? (
          <>
            <div style={styles.filtrosCert}>
              <input
                style={styles.inputFiltro}
                placeholder="Filtrar por radicado"
                value={filtroCertRadicado}
                onChange={(e) => setFiltroCertRadicado(e.target.value)}
              />
              <input
                style={styles.inputFiltro}
                placeholder="Filtrar por solicitante"
                value={filtroCertNombre}
                onChange={(e) => setFiltroCertNombre(e.target.value)}
              />
              <select style={styles.inputFiltro} value={filtroCertTipo} onChange={(e) => setFiltroCertTipo(e.target.value)}>
                <option value="todos">Todas las respuestas</option>
                <option value="positiva">Positivas</option>
                <option value="negativa">Negativas</option>
              </select>
            </div>

            {certificadosGeneradosFiltrados.length === 0 ? (
              <div style={styles.listaVacia}>No hay certificados generados para los filtros seleccionados</div>
            ) : (
              <div style={styles.listaCertificados}>
                {certificadosGeneradosFiltrados.map((cert) => (
                  <div key={`cert-${cert.id}`} style={styles.certItem}>
                    <p style={styles.certMeta}><strong>{cert.numeroRadicado}</strong> · {cert.nombreSolicitante}</p>
                    <p style={styles.certMeta}>Respuesta: {cert.estado === 'FINALIZADO' ? 'Positiva' : 'Negativa'} · Tipo: {cert.tipo_certificado || 'Certificado'}</p>
                    <p style={styles.certMeta}>Fecha: {formatearFecha(cert.fechaVerificacion || cert.fechaFirmaAlcalde || cert.fechaRadicacion)}</p>
                  </div>
                ))}
              </div>
            )}
          </>
        ) : null}
      </div>

      <div style={{ ...styles.panelContenido, ...(isMobile ? styles.panelContenidoMobile : styles.panelContenidoDesktop) }}>
        <div style={{ ...styles.panelLista, ...(isMobile ? {} : styles.panelListaDesktop) }}>
          <h3 style={styles.h3Lista}>
            {filtroActual.titulo} ({solicitudesFiltradasBusqueda.length})
          </h3>

          <div style={styles.busquedaWrap}>
            <input
              style={styles.busquedaInput}
              placeholder="Buscar por radicado, solicitante, documento, estado o tipo..."
              value={busquedaSolicitudes}
              onChange={(e) => setBusquedaSolicitudes(e.target.value)}
            />
            {busquedaSolicitudes.trim() ? (
              <button style={styles.busquedaBtn} onClick={() => setBusquedaSolicitudes('')}>
                Limpiar
              </button>
            ) : null}
            <p style={styles.busquedaMeta}>Mostrando {solicitudesFiltradasBusqueda.length} de {solicitudesFiltradas.length}</p>
          </div>

          {solicitudesFiltradas.length === 0 ? (
            <div style={styles.listaVacia}>
              <p style={{ margin: '8px 0', fontSize: '14px' }}>No hay solicitudes para esta vista</p>
              <p style={styles.subtexto}>Selecciona otro filtro para revisar el historial</p>
            </div>
          ) : solicitudesFiltradasBusqueda.length === 0 ? (
            <div style={styles.listaVacia}>
              <p style={{ margin: '8px 0', fontSize: '14px' }}>No hay solicitudes que coincidan con la búsqueda</p>
            </div>
          ) : (
            <div style={{ ...styles.listaSolicitudes, ...(isMobile ? styles.listaSolicitudesMobile : styles.listaSolicitudesDesktop) }}>
              {solicitudesFiltradasBusqueda.map((solicitud) => (
                <div
                  key={solicitud.id}
                  style={{ ...styles.solicitudItem, ...(selectedSolicitud?.id === solicitud.id ? styles.solicitudActiva : {}) }}
                  onClick={() => setSelectedSolicitud(solicitud)}
                >
                  <div style={styles.fila}>
                    <span style={styles.radicado}>{solicitud.numeroRadicado}</span>
                    <span style={styles.badge}>{getEstadoBadge(solicitud.estado)}</span>
                  </div>
                  <p style={styles.itemP}>
                    <strong style={styles.itemStrong}>{solicitud.nombreSolicitante}</strong>
                  </p>
                  <p style={styles.itemP}>{solicitud.tipo_certificado || 'Certificado'}</p>
                  <p style={{ ...styles.itemP, color: '#95a5a6', fontSize: '12px' }}>
                    Listo desde: {formatearFecha(solicitud.fechaVerificacion || solicitud.fechaFirmaAlcalde || solicitud.fechaRadicacion)}
                  </p>
                </div>
              ))}
            </div>
          )}
        </div>

        {selectedSolicitud ? (
          <div style={styles.panelDetalle}>
            <div style={styles.detalleHeader}>
              <h3 style={styles.detalleH3}>{esPendienteFirma ? '✍️ Firmar Certificado' : '📄 Detalle de Solicitud'}</h3>
              <button
                style={styles.btnCerrar}
                onClick={() => {
                  setSelectedSolicitud(null);
                  setFirmaDigital('');
                }}
              >
                ✕
              </button>
            </div>

            <div style={styles.contenido}>
              <div style={styles.seccion}>
                <h4 style={styles.h4}>📋 Información del Certificado</h4>
                <div style={styles.infoGrid}>
                  <div style={styles.infoItem}><label style={styles.label}>Número de Radicado</label><p style={styles.infoP}>{selectedSolicitud.numeroRadicado}</p></div>
                  <div style={styles.infoItem}><label style={styles.label}>Beneficiario</label><p style={styles.infoP}>{selectedSolicitud.nombreSolicitante}</p></div>
                  <div style={styles.infoItem}><label style={styles.label}>Documento</label><p style={styles.infoP}>{selectedSolicitud.numeroDocumento}</p></div>
                  <div style={styles.infoItem}><label style={styles.label}>Lugar de Expedición</label><p style={styles.infoP}>{selectedSolicitud.lugarExpedicionDocumento || 'No registrado'}</p></div>
                  <div style={styles.infoItem}><label style={styles.label}>Tipo de Certificado</label><p style={styles.infoP}>{selectedSolicitud.tipo_certificado || 'Certificado de Residencia'}</p></div>
                  <div style={styles.infoItem}><label style={styles.label}>Dirección</label><p style={styles.infoP}>{selectedSolicitud.direccionResidencia}</p></div>
                  <div style={styles.infoItem}><label style={styles.label}>Barrio</label><p style={styles.infoP}>{selectedSolicitud.barrioResidencia || 'No registrado'}</p></div>
                  <div style={styles.infoItem}>
                    <label style={styles.label}>Vigencia</label>
                    <p style={styles.infoP}>
                      Desde: {formatearFecha(selectedSolicitud.fechaRadicacion)}<br />
                      Hasta: {formatearFecha(selectedSolicitud.fechaVigencia)}
                    </p>
                  </div>
                </div>
              </div>

              <div style={styles.seccion}>
                <h4 style={styles.h4}>👁️ Vista Previa de Certificado</h4>
                <div style={styles.vistaWrap}>
                  {loadingVistaPrevia ? (
                    <p>Cargando vista previa...</p>
                  ) : vistaPreviaPdf ? (
                    <iframe title="Vista previa PDF" style={styles.previewFrame} src={`data:application/pdf;base64,${vistaPreviaPdf}`} />
                  ) : vistaPreviaHtml ? (
                    <iframe title="Vista previa documento" style={styles.previewFrame} srcDoc={vistaPreviaHtml} />
                  ) : (
                    <pre style={styles.previewTexto}>{vistaPrevia}</pre>
                  )}
                </div>
              </div>

              {esPendienteFirma ? (
                <div style={styles.seccion}>
                  <h4 style={styles.h4}>🔐 Firma Digital</h4>
                  <input
                    type="password"
                    value={firmaDigital}
                    onChange={(e) => setFirmaDigital(e.target.value)}
                    placeholder="••••••••"
                    style={styles.inputFirma}
                    onKeyPress={(e) => e.key === 'Enter' && handleFirmar()}
                  />
                  <div style={styles.accionesFirma}>
                    <button
                      style={{ ...styles.btnFirmar, ...(isMobile ? { width: '100%', minWidth: 0 } : {}), ...(procesando || firmaDigital.trim().length === 0 ? styles.btnFirmarDisabled : {}) }}
                      onClick={handleFirmar}
                      disabled={procesando || firmaDigital.trim().length === 0}
                    >
                      {procesando ? '⏳ Firmando...' : '✍️ Firmar y Emitir Certificado'}
                    </button>
                  </div>
                </div>
              ) : null}

              <div style={styles.infoLegal}>
                <p style={styles.infoLegalP}>
                  {esPendienteFirma ? (
                    <><strong style={{ color: '#000' }}>⚖️ Nota Legal:</strong> Al firmar, confirmas que has revisado la información y autorizas la emisión del certificado.</>
                  ) : (
                    <><strong style={{ color: '#000' }}>ℹ️ Información:</strong> Esta solicitud ya fue procesada y se muestra en modo consulta.</>
                  )}
                </p>
              </div>
            </div>
          </div>
        ) : null}
      </div>
    </div>
  );
}
