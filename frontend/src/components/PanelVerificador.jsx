import { useEffect, useMemo, useState } from 'react';
import { API_TRAMITES_URL } from '../config/api';

const FILTROS = [
  { key: 'pendientes', label: 'Pendientes', titulo: '📋 Solicitudes Pendientes' },
  { key: 'aprobadas', label: 'Aprobadas', titulo: '✅ Solicitudes Aprobadas' },
  { key: 'negadas', label: 'Negadas', titulo: '❌ Solicitudes Negadas' },
];

const styles = {
  loading: { textAlign: 'center', padding: '40px', fontSize: '16px', color: '#7f8c8d' },
  contenedor: { maxWidth: '1600px', margin: '0 auto', padding: 'clamp(0.75rem, 3.5vw, 1.25rem)', background: 'linear-gradient(135deg, #f5f7fa 0%, #c3cfe2 100%)', minHeight: '100vh' },
  header: { background: '#fff', padding: 'clamp(0.9rem, 4vw, 1.5rem)', borderRadius: '12px', boxShadow: '0 2px 8px rgba(0,0,0,.08)', marginBottom: '20px' },
  h2: { margin: '0 0 8px 0', color: '#2c3e50', fontSize: 'clamp(1.15rem, 5vw, 1.75rem)' },
  subtitle: { margin: 0, color: '#7f8c8d', fontSize: '14px' },
  filtros: { display: 'flex', gap: '8px', marginTop: '14px', flexWrap: 'nowrap', overflowX: 'auto', overflowY: 'hidden', WebkitOverflowScrolling: 'touch' },
  btnFiltro: { border: '1px solid #cfd8dc', background: '#fff', color: '#2c3e50', padding: '6px 10px', borderRadius: '8px', cursor: 'pointer', fontWeight: 600, width: 'auto', display: 'inline-flex', alignItems: 'center', justifyContent: 'center', flex: '0 0 auto', whiteSpace: 'nowrap', fontSize: '0.85rem' },
  btnFiltroActivo: { background: '#2d7ff9', color: '#fff', borderColor: '#2d7ff9' },
  btnConsolidado: { border: 'none', background: '#2d7ff9', color: '#fff', padding: '6px 10px', borderRadius: '8px', cursor: 'pointer', fontWeight: 600, marginTop: '8px', width: 'auto', display: 'inline-flex', alignItems: 'center', justifyContent: 'center', whiteSpace: 'nowrap', fontSize: '0.85rem' },
  error: { background: '#ffe0e0', border: '1px solid #ff6b6b', color: '#c92a2a', padding: '16px', borderRadius: '8px', marginBottom: '20px' },
  layout: { display: 'grid', gap: '14px' },
  layoutDesktop: { gridTemplateColumns: '420px minmax(0, 1fr)' },
  layoutMobile: { gridTemplateColumns: '1fr' },
  lista: { background: '#fff', padding: '14px', borderRadius: '12px', boxShadow: '0 2px 8px rgba(0,0,0,.08)', height: 'fit-content' },
  listaDesktop: { position: 'sticky', top: '20px' },
  listaTitulo: { margin: '0 0 12px 0', fontSize: '16px', color: '#2c3e50' },
  listaVacia: { textAlign: 'center', padding: '24px 10px', color: '#95a5a6' },
  listaScroll: { display: 'flex', flexDirection: 'column', gap: '10px', overflowY: 'auto', overflowX: 'hidden', WebkitOverflowScrolling: 'touch' },
  listaScrollDesktop: { maxHeight: 'calc(100vh - 260px)' },
  listaScrollMobile: { maxHeight: '50vh' },
  item: { padding: '12px', border: '2px solid #ecf0f1', borderRadius: '8px', cursor: 'pointer', background: '#f9fafb' },
  itemActivo: { borderColor: '#2980b9', background: '#e3f2fd', boxShadow: '0 0 0 3px rgba(52,152,219,.1)' },
  row: { display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '8px', gap: '8px' },
  radicado: { fontWeight: 'bold', color: '#2c3e50', fontFamily: 'monospace', fontSize: '12px' },
  badge: { fontSize: '11px', padding: '4px 8px', borderRadius: '4px', fontWeight: 600, textTransform: 'uppercase' },
  itemP: { margin: '3px 0', fontSize: '13px', color: '#2c3e50' },
  detalle: { background: '#fff', borderRadius: '12px', boxShadow: '0 2px 8px rgba(0,0,0,.08)', overflow: 'hidden' },
  detalleHeader: { background: 'linear-gradient(135deg,#667eea 0%,#764ba2 100%)', color: '#fff', padding: '18px', display: 'flex', justifyContent: 'space-between', alignItems: 'center' },
  detalleHeaderH3: { margin: 0, fontSize: '18px' },
  cerrar: { background: 'rgba(255,255,255,.2)', border: 'none', color: '#fff', width: '36px', height: '36px', borderRadius: '50%', cursor: 'pointer', fontSize: '20px' },
  detalleBody: { padding: 'clamp(0.9rem, 3.5vw, 1.2rem)', maxHeight: 'calc(100vh - 230px)', overflowY: 'auto' },
  seccion: { marginBottom: '18px', paddingBottom: '16px', borderBottom: '1px solid #ecf0f1' },
  h4: { margin: '0 0 12px 0', fontSize: '15px', color: '#2c3e50' },
  grid2: { display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px,1fr))', gap: '12px' },
  cardInfo: { background: '#f9fafb', padding: '10px', borderRadius: '6px', borderLeft: '3px solid #3498db' },
  label: { display: 'block', fontSize: '12px', color: '#7f8c8d', textTransform: 'uppercase', fontWeight: 600, marginBottom: '6px' },
  value: { margin: 0, fontSize: '14px', color: '#2c3e50', fontWeight: 500 },
  input: { width: '100%', border: '1px solid #cfd8dc', borderRadius: '6px', padding: '8px 10px', fontSize: '14px', boxSizing: 'border-box' },
  docs: { display: 'flex', flexDirection: 'column', gap: '10px' },
  docItem: { display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: '10px', padding: '10px', background: '#f9fafb', borderRadius: '6px', borderLeft: '3px solid #f39c12' },
  docBtns: { display: 'flex', gap: '8px' },
  btnVer: { background: '#3498db', color: '#fff', border: 'none', padding: '6px 10px', borderRadius: '4px', cursor: 'pointer', fontSize: '12px', fontWeight: 600 },
  btnDesc: { background: '#27ae60', color: '#fff', border: 'none', padding: '6px 10px', borderRadius: '4px', cursor: 'pointer', fontSize: '12px', fontWeight: 600 },
  textarea: { width: '100%', minHeight: '110px', padding: '12px', border: '1px solid #bdc3c7', borderRadius: '6px', fontSize: '13px', boxSizing: 'border-box', resize: 'vertical' },
  acciones: { display: 'flex', gap: '10px', paddingTop: '14px', flexWrap: 'wrap' },
  btnAprobar: { background: '#27ae60', color: '#fff', border: 'none', borderRadius: '6px', padding: '10px 14px', fontWeight: 600, cursor: 'pointer', width: 'auto', minWidth: '190px' },
  btnRechazar: { background: '#e74c3c', color: '#fff', border: 'none', borderRadius: '6px', padding: '10px 14px', fontWeight: 600, cursor: 'pointer', width: 'auto', minWidth: '190px' },
  disabled: { background: '#bdc3c7', cursor: 'not-allowed', opacity: 0.7 },
  nota: { background: '#e3f2fd', borderLeft: '4px solid #2196F3', padding: '12px', borderRadius: '4px', marginTop: '16px', color: '#1565c0', fontSize: '13px' },
  certificadosSeccion: { background: '#fff', padding: '14px', borderRadius: '12px', boxShadow: '0 2px 8px rgba(0,0,0,.08)', marginBottom: '14px' },
  filtrosCert: { display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: '8px', marginBottom: '10px' },
  inputFiltro: { width: '100%', border: '1px solid #cfd8dc', borderRadius: '6px', padding: '8px 10px', fontSize: '13px', boxSizing: 'border-box' },
  listaCertificados: { display: 'grid', gap: '8px', maxHeight: '220px', overflowY: 'auto' },
  certItem: { border: '1px solid #e5e7eb', borderRadius: '8px', padding: '8px 10px', background: '#f9fafb' },
  certMeta: { margin: '2px 0', fontSize: '12px', color: '#4b5563' },
  seccionHeader: { display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: '0.8rem', marginBottom: '10px', flexWrap: 'wrap' },
  btnToggleSeccion: { padding: '0.35rem 0.75rem', background: '#4f46e5', color: '#fff', border: 'none', borderRadius: '6px', cursor: 'pointer', fontWeight: 600, fontSize: '0.78rem', width: 'auto', whiteSpace: 'nowrap' },
};

const estadoBadge = (estado) => {
  const key = (estado || '').toLowerCase();
  if (key === 'radicado') return { text: '🟡 Nuevo', style: { background: '#fff9e6', color: '#f39c12' } };
  if (key === 'en_validacion') return { text: '🟠 En Validación', style: { background: '#ffe6e6', color: '#e74c3c' } };
  if (key === 'en_firma') return { text: '🔵 Aprobada', style: { background: '#e6f3ff', color: '#3498db' } };
  if (key === 'finalizado') return { text: '🟢 Finalizada', style: { background: '#e6ffe6', color: '#27ae60' } };
  return { text: '🔴 Negada', style: { background: '#ffe6e6', color: '#e74c3c' } };
};

const cumpleFiltro = (solicitud, filtro) => {
  if (filtro === 'pendientes') return solicitud.estado === 'EN_VALIDACION' || solicitud.estado === 'RADICADO';
  if (filtro === 'aprobadas') return solicitud.estado === 'EN_FIRMA' || solicitud.estado === 'FINALIZADO';
  return solicitud.estado === 'RECHAZADO';
};

export default function PanelVerificador() {
  const [solicitudes, setSolicitudes] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [selectedSolicitud, setSelectedSolicitud] = useState(null);
  const [observaciones, setObservaciones] = useState('');
  const [consecutivo, setConsecutivo] = useState('');
  const [filtroVista, setFiltroVista] = useState('pendientes');
  const [procesando, setProcesando] = useState(false);
  const [documentStatus, setDocumentStatus] = useState(null);
  const [loadingDocumentos, setLoadingDocumentos] = useState(false);
  const [isMobile, setIsMobile] = useState(typeof window !== 'undefined' ? window.innerWidth < 992 : false);
  const [filtroCertRadicado, setFiltroCertRadicado] = useState('');
  const [filtroCertNombre, setFiltroCertNombre] = useState('');
  const [filtroCertTipo, setFiltroCertTipo] = useState('todos');
  const [certificadosExpandido, setCertificadosExpandido] = useState(false);

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
      cargarEstadoDocumentos(selectedSolicitud.id);
      return;
    }
    setDocumentStatus(null);
  }, [selectedSolicitud]);

  const filtroActual = useMemo(() => FILTROS.find((f) => f.key === filtroVista) || FILTROS[0], [filtroVista]);
  const solicitudesFiltradas = useMemo(() => solicitudes.filter((s) => cumpleFiltro(s, filtroVista)), [solicitudes, filtroVista]);
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
  const esPendienteSeleccionada = selectedSolicitud ? (selectedSolicitud.estado === 'EN_VALIDACION' || selectedSolicitud.estado === 'RADICADO') : false;

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

  const cargarEstadoDocumentos = async (tramiteId) => {
    setLoadingDocumentos(true);
    try {
      const response = await fetch(`${API_TRAMITES_URL}/${tramiteId}/verificar-documentos`);
      if (!response.ok) throw new Error('No se pudo cargar el estado de documentos');
      const data = await response.json();
      setDocumentStatus(data);
    } catch {
      setDocumentStatus(null);
    } finally {
      setLoadingDocumentos(false);
    }
  };

  const abrirDocumento = async (tramiteId, tipo) => {
    try {
      const response = await fetch(`${API_TRAMITES_URL}/${tramiteId}/descargar/${tipo}`);
      if (!response.ok) throw new Error('Documento no disponible para abrir');
      const blob = await response.blob();
      const url = window.URL.createObjectURL(blob);
      window.open(url, '_blank', 'noopener,noreferrer');
    } catch (err) {
      alert(`❌ Error al abrir: ${err.message}`);
    }
  };

  const descargarDocumento = async (tramiteId, tipo) => {
    try {
      const response = await fetch(`${API_TRAMITES_URL}/${tramiteId}/descargar/${tipo}`);
      if (!response.ok) throw new Error('Documento no disponible para descargar');
      const blob = await response.blob();
      const url = window.URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = `${tipo}_${tramiteId}`;
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      window.URL.revokeObjectURL(url);
    } catch (err) {
      alert(`❌ Error al descargar: ${err.message}`);
    }
  };

  const descargarConsolidadoExcel = async () => {
    try {
      const response = await fetch(`${API_TRAMITES_URL}/reporte/consolidado-verificaciones`);
      if (!response.ok) throw new Error('No se pudo generar el consolidado');
      const blob = await response.blob();
      const url = window.URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = 'consolidado_verificaciones.xlsx';
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      window.URL.revokeObjectURL(url);
    } catch (err) {
      alert(`❌ Error al descargar consolidado: ${err.message}`);
    }
  };

  const handleAprobar = async () => {
    if (!selectedSolicitud) return;
    if (!consecutivo.trim()) return alert('Por favor, registra el consecutivo del verificador');

    setProcesando(true);
    try {
      const userGuardado = localStorage.getItem('user');
      const user = userGuardado ? JSON.parse(userGuardado) : null;

      const response = await fetch(`${API_TRAMITES_URL}/${selectedSolicitud.id}/verificacion`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          aprobado: true,
          consecutivo: consecutivo.trim(),
          observaciones: observaciones || 'Aprobado por verificador',
          username: user?.username || 'verificador',
        }),
      });
      if (!response.ok) throw new Error('Error al aprobar');

      alert('✅ Solicitud aprobada y enviada al alcalde para firma');
      setSelectedSolicitud(null);
      setObservaciones('');
      setConsecutivo('');
      await cargarSolicitudes();
    } catch (err) {
      alert(`❌ Error: ${err.message}`);
    } finally {
      setProcesando(false);
    }
  };

  const handleRechazar = async () => {
    if (!selectedSolicitud) return;
    if (!observaciones.trim()) return alert('Por favor, proporciona una razón para el rechazo');
    if (!consecutivo.trim()) return alert('Por favor, registra el consecutivo del verificador');

    setProcesando(true);
    try {
      const userGuardado = localStorage.getItem('user');
      const user = userGuardado ? JSON.parse(userGuardado) : null;

      const response = await fetch(`${API_TRAMITES_URL}/${selectedSolicitud.id}/verificacion`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          aprobado: false,
          consecutivo: consecutivo.trim(),
          observaciones: observaciones.trim(),
          username: user?.username || 'verificador',
        }),
      });
      if (!response.ok) throw new Error('Error al rechazar');

      alert('⛔ Solicitud rechazada. Se ha notificado al solicitante.');
      setSelectedSolicitud(null);
      setObservaciones('');
      setConsecutivo('');
      await cargarSolicitudes();
    } catch (err) {
      alert(`❌ Error: ${err.message}`);
    } finally {
      setProcesando(false);
    }
  };

  if (loading) return <div style={styles.loading}>Cargando solicitudes...</div>;

  return (
    <div style={styles.contenedor}>
      <div style={styles.header}>
        <h2 style={styles.h2}>👤 Panel del Verificador</h2>
        <p style={styles.subtitle}>Revisa y aprueba las solicitudes de certificado de residencia</p>
        <div style={styles.filtros}>
          {FILTROS.map((filtro) => (
            <button
              key={filtro.key}
              style={{ ...styles.btnFiltro, ...(filtroVista === filtro.key ? styles.btnFiltroActivo : {}) }}
              onClick={() => {
                setFiltroVista(filtro.key);
                setSelectedSolicitud(null);
                setObservaciones('');
                setConsecutivo('');
              }}
            >
              {filtro.label}
            </button>
          ))}
        </div>
        <button style={styles.btnConsolidado} onClick={descargarConsolidadoExcel}>📊 Descargar consolidado Excel</button>
      </div>

      {error ? <div style={styles.error}>⚠️ {error}</div> : null}

      <div style={styles.certificadosSeccion}>
        <div style={styles.seccionHeader}>
          <h3 style={{ ...styles.listaTitulo, margin: 0 }}>📄 Certificados Generados ({certificadosGeneradosFiltrados.length})</h3>
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
                    <p style={styles.certMeta}>Fecha: {new Date(cert.fechaRadicacion).toLocaleDateString('es-CO')}</p>
                  </div>
                ))}
              </div>
            )}
          </>
        ) : null}
      </div>

      <div style={{ ...styles.layout, ...(isMobile ? styles.layoutMobile : styles.layoutDesktop) }}>
        <div style={{ ...styles.lista, ...(isMobile ? {} : styles.listaDesktop) }}>
          <h3 style={styles.listaTitulo}>{filtroActual.titulo} ({solicitudesFiltradas.length})</h3>

          {solicitudesFiltradas.length === 0 ? (
            <div style={styles.listaVacia}>No hay solicitudes para esta vista</div>
          ) : (
            <div style={{ ...styles.listaScroll, ...(isMobile ? styles.listaScrollMobile : styles.listaScrollDesktop) }}>
              {solicitudesFiltradas.map((solicitud) => {
                const badge = estadoBadge(solicitud.estado);
                return (
                  <div
                    key={solicitud.id}
                    style={{ ...styles.item, ...(selectedSolicitud?.id === solicitud.id ? styles.itemActivo : {}) }}
                    onClick={() => {
                      setSelectedSolicitud(solicitud);
                      setConsecutivo(solicitud.consecutivoVerificador || '');
                      setObservaciones(solicitud.observaciones || '');
                    }}
                  >
                    <div style={styles.row}>
                      <span style={styles.radicado}>{solicitud.numeroRadicado}</span>
                      <span style={{ ...styles.badge, ...badge.style }}>{badge.text}</span>
                    </div>
                    <p style={styles.itemP}><strong>{solicitud.nombreSolicitante}</strong></p>
                    <p style={styles.itemP}>{solicitud.numeroDocumento}</p>
                    <p style={{ ...styles.itemP, color: '#95a5a6', fontSize: '12px' }}>
                      Radicado: {new Date(solicitud.fechaRadicacion).toLocaleDateString('es-CO')}
                    </p>
                  </div>
                );
              })}
            </div>
          )}
        </div>

        {selectedSolicitud ? (
          <div style={styles.detalle}>
            <div style={styles.detalleHeader}>
              <h3 style={styles.detalleHeaderH3}>🔍 Verificación Detallada</h3>
              <button
                style={styles.cerrar}
                onClick={() => {
                  setSelectedSolicitud(null);
                  setObservaciones('');
                  setConsecutivo('');
                }}
              >
                ✕
              </button>
            </div>

            <div style={styles.detalleBody}>
              <div style={styles.seccion}>
                <h4 style={styles.h4}>📝 Información del Solicitante</h4>
                <div style={styles.grid2}>
                  <div style={styles.cardInfo}><span style={styles.label}>Nombre</span><p style={styles.value}>{selectedSolicitud.nombreSolicitante}</p></div>
                  <div style={styles.cardInfo}><span style={styles.label}>Documento</span><p style={styles.value}>{selectedSolicitud.numeroDocumento}</p></div>
                  <div style={styles.cardInfo}><span style={styles.label}>Correo</span><p style={styles.value}>{selectedSolicitud.correoElectronico}</p></div>
                  <div style={styles.cardInfo}><span style={styles.label}>Teléfono</span><p style={styles.value}>{selectedSolicitud.telefono}</p></div>
                  <div style={styles.cardInfo}><span style={styles.label}>Dirección</span><p style={styles.value}>{selectedSolicitud.direccionResidencia}</p></div>
                  <div style={styles.cardInfo}><span style={styles.label}>Barrio</span><p style={styles.value}>{selectedSolicitud.barrioResidencia || 'No registrado'}</p></div>
                </div>
              </div>

              <div style={styles.seccion}>
                <h4 style={styles.h4}>📋 Información del Trámite</h4>
                <div style={styles.grid2}>
                  <div style={styles.cardInfo}><span style={styles.label}>Radicado</span><p style={styles.value}>{selectedSolicitud.numeroRadicado}</p></div>
                  <div style={styles.cardInfo}><span style={styles.label}>Estado</span><p style={styles.value}>{selectedSolicitud.estado}</p></div>
                  <div style={styles.cardInfo}><span style={styles.label}>Tipo Certificado</span><p style={styles.value}>{selectedSolicitud.tipo_certificado || 'No especificado'}</p></div>
                  <div style={styles.cardInfo}><span style={styles.label}>Fecha Radicación</span><p style={styles.value}>{new Date(selectedSolicitud.fechaRadicacion).toLocaleDateString('es-CO')}</p></div>
                  <div style={styles.cardInfo}><span style={styles.label}>Consecutivo Verificador</span><input style={styles.input} value={consecutivo} onChange={(e) => setConsecutivo(e.target.value.toUpperCase())} readOnly={!esPendienteSeleccionada} /></div>
                </div>
              </div>

              <div style={styles.seccion}>
                <h4 style={styles.h4}>📂 Documentos Adjuntos</h4>
                <div style={styles.docs}>
                  {[
                    { key: 'identidad', label: '📄 Documento de Identidad' },
                    { key: 'solicitud', label: '📄 Documento de Solicitud' },
                    { key: (selectedSolicitud.tipo_certificado?.toLowerCase() || 'sisben'), label: `🎓 Certificado (${selectedSolicitud.tipo_certificado || 'SISBEN'})` },
                  ].map((doc) => {
                    const disponible = loadingDocumentos ? false : !!documentStatus?.[doc.key]?.cargado;
                    return (
                      <div key={doc.label} style={styles.docItem}>
                        <span>{doc.label}</span>
                        {loadingDocumentos ? (
                          <span style={{ color: '#95a5a6', fontSize: '12px' }}>Cargando...</span>
                        ) : disponible ? (
                          <div style={styles.docBtns}>
                            <button style={styles.btnVer} onClick={() => abrirDocumento(selectedSolicitud.id, doc.key)}>Ver</button>
                            <button style={styles.btnDesc} onClick={() => descargarDocumento(selectedSolicitud.id, doc.key)}>Descargar</button>
                          </div>
                        ) : (
                          <span style={{ color: '#95a5a6', fontSize: '12px' }}>No disponible</span>
                        )}
                      </div>
                    );
                  })}
                </div>
              </div>

              <div style={styles.seccion}>
                <h4 style={styles.h4}>💭 Observaciones</h4>
                {esPendienteSeleccionada ? (
                  <>
                    <textarea
                      style={styles.textarea}
                      value={observaciones}
                      onChange={(e) => setObservaciones(e.target.value)}
                      placeholder={selectedSolicitud.estado === 'RADICADO' ? 'Escribe observaciones si vas a rechazar, o déjalo en blanco para aprobar...' : 'Escribe aquí tus observaciones'}
                    />
                    <div style={styles.acciones}>
                      <button style={{ ...styles.btnAprobar, ...(isMobile ? { width: '100%', minWidth: 0 } : {}), ...(procesando || observaciones.trim().length > 0 ? styles.disabled : {}) }} onClick={handleAprobar} disabled={procesando || observaciones.trim().length > 0}>
                        {procesando ? '⏳ Procesando...' : '✅ Aprobar Solicitud'}
                      </button>
                      <button style={{ ...styles.btnRechazar, ...(isMobile ? { width: '100%', minWidth: 0 } : {}), ...(procesando || observaciones.trim().length === 0 ? styles.disabled : {}) }} onClick={handleRechazar} disabled={procesando || observaciones.trim().length === 0}>
                        {procesando ? '⏳ Procesando...' : '❌ Rechazar Solicitud'}
                      </button>
                    </div>
                  </>
                ) : (
                  <p style={{ margin: 0 }}>{selectedSolicitud.observaciones || 'Sin observaciones registradas'}</p>
                )}
              </div>

              <div style={styles.nota}>
                {esPendienteSeleccionada
                  ? '📌 Si apruebas, la solicitud se envía al alcalde para firma. Si rechazas, se notificará al solicitante con la observación.'
                  : '📌 Esta solicitud ya fue procesada por verificación.'}
              </div>
            </div>
          </div>
        ) : null}
      </div>
    </div>
  );
}
