import { useCallback, useEffect, useMemo, useState } from 'react';
import { API_TRAMITES_URL } from '../config/api';
import { formatearFechaHora as formatearFechaHoraUtil } from '../utils/dateFormat';
import { buildUsernameHeader, getStoredUser } from '../utils/userSession';
import { filtrarCertificadosGenerados } from '../utils/certificateFilters';
import AvisoModal from './common/AvisoModal';

import { getPanelVerificadorStyles } from '../styles/components/PanelVerificadorStyles';
const FILTROS = [
  { key: 'pendientes', label: 'Pendientes', titulo: '📋 Solicitudes Pendientes' },
  { key: 'aprobadas', label: 'Aprobadas', titulo: '✅ Solicitudes Aprobadas' },
  { key: 'negadas', label: 'Negadas', titulo: '❌ Solicitudes Negadas' },
];

const styles = getPanelVerificadorStyles();

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

const formatearFechaHora = (valor) => formatearFechaHoraUtil(valor, { fallback: 'Sin fecha' });

const normalizarFecha = (valor) => {
  if (!valor) return null;
  const fecha = new Date(valor);
  if (Number.isNaN(fecha.getTime())) return null;
  return new Date(fecha.getFullYear(), fecha.getMonth(), fecha.getDate());
};

const esDiaHabil = (fecha) => {
  const dia = fecha.getDay();
  return dia !== 0 && dia !== 6;
};

const calcularDiasHabilesEntre = (inicio, fin) => {
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

const obtenerTextoDiasHabilesRestantes = (fechaVencimiento) => {
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

export default function PanelVerificador() {
  const [solicitudes, setSolicitudes] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [selectedSolicitud, setSelectedSolicitud] = useState(null);
  const [observaciones, setObservaciones] = useState('');
  const [consecutivo, setConsecutivo] = useState('');
  const [filtroVista, setFiltroVista] = useState('pendientes');
  const [busquedaSolicitudes, setBusquedaSolicitudes] = useState('');
  const [procesando, setProcesando] = useState(false);
  const [documentStatus, setDocumentStatus] = useState(null);
  const [loadingDocumentos, setLoadingDocumentos] = useState(false);
  const [isMobile, setIsMobile] = useState(typeof window !== 'undefined' ? window.innerWidth < 992 : false);
  const [filtroCertRadicado, setFiltroCertRadicado] = useState('');
  const [filtroCertNombre, setFiltroCertNombre] = useState('');
  const [filtroCertTipo, setFiltroCertTipo] = useState('todos');
  const [certificadosExpandido, setCertificadosExpandido] = useState(false);
  const [enviandoNotificacionAdmin, setEnviandoNotificacionAdmin] = useState(false);
  const [avisoPanel, setAvisoPanel] = useState(null);

  const mostrarAvisoPanel = (tipo, mensaje) => {
    setAvisoPanel({ tipo, mensaje });
  };

  useEffect(() => {
    cargarSolicitudes();
  }, []);

  useEffect(() => {
    const onResize = () => setIsMobile(window.innerWidth < 992);
    window.addEventListener('resize', onResize);
    return () => window.removeEventListener('resize', onResize);
  }, []);

  const filtroActual = useMemo(() => FILTROS.find((f) => f.key === filtroVista) || FILTROS[0], [filtroVista]);
  const solicitudesFiltradas = useMemo(() => solicitudes.filter((s) => cumpleFiltro(s, filtroVista)), [solicitudes, filtroVista]);
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
  const certificadosGeneradosFiltrados = useMemo(() => filtrarCertificadosGenerados(solicitudes, {
    radicado: filtroCertRadicado,
    nombre: filtroCertNombre,
    tipo: filtroCertTipo,
  }), [solicitudes, filtroCertRadicado, filtroCertNombre, filtroCertTipo]);
  const esPendienteSeleccionada = selectedSolicitud ? (selectedSolicitud.estado === 'EN_VALIDACION' || selectedSolicitud.estado === 'RADICADO') : false;
  const textoDiasHabilesSeleccionada = useMemo(() => {
    if (!selectedSolicitud || !esPendienteSeleccionada) return null;
    return obtenerTextoDiasHabilesRestantes(selectedSolicitud.fechaVencimiento);
  }, [selectedSolicitud, esPendienteSeleccionada]);
  const documentosAdjuntos = useMemo(() => {
    if (!selectedSolicitud) return [];

    const tipoCertificado = (selectedSolicitud.tipo_certificado || '').toLowerCase();
    let claveTercerDocumento = tipoCertificado === 'jac'
      ? 'residencia'
      : (tipoCertificado || 'sisben');

    if (!loadingDocumentos && documentStatus && !documentStatus?.[claveTercerDocumento]?.cargado) {
      const claveDisponible = ['sisben', 'electoral', 'residencia'].find((key) => documentStatus?.[key]?.cargado);
      if (claveDisponible) {
        claveTercerDocumento = claveDisponible;
      }
    }

    const etiquetaTercerDocumento = claveTercerDocumento === 'residencia'
      ? (selectedSolicitud.tipo_certificado ? `🎓 Certificado (${selectedSolicitud.tipo_certificado})` : '🎓 Certificado (JAC/Residencia)')
      : `🎓 Certificado (${(selectedSolicitud.tipo_certificado || claveTercerDocumento).toUpperCase()})`;

    return [
      { key: 'identidad', label: '📄 Documento de Identidad' },
      { key: 'solicitud', label: '📄 Documento de Solicitud' },
      { key: claveTercerDocumento, label: etiquetaTercerDocumento },
    ];
  }, [selectedSolicitud, documentStatus, loadingDocumentos]);

  const totalDocumentosMostrados = useMemo(() => {
    if (!documentStatus || loadingDocumentos || !selectedSolicitud) return 0;
    return documentosAdjuntos.reduce((acc, doc) => acc + (documentStatus?.[doc.key]?.cargado ? 1 : 0), 0);
  }, [documentStatus, loadingDocumentos, selectedSolicitud, documentosAdjuntos]);

  const hayDesfaseDocumentos = useMemo(() => {
    if (!documentStatus || loadingDocumentos || !selectedSolicitud) return false;
    return (documentStatus.totalDocumentosCargados || 0) > totalDocumentosMostrados;
  }, [documentStatus, loadingDocumentos, selectedSolicitud, totalDocumentosMostrados]);

  const documentosFaltantes = useMemo(() => {
    if (!documentStatus || loadingDocumentos || !selectedSolicitud) return [];
    return documentosAdjuntos.filter((doc) => !documentStatus?.[doc.key]?.cargado).map((doc) => doc.label);
  }, [documentStatus, loadingDocumentos, selectedSolicitud, documentosAdjuntos]);

  const documentosObligatoriosCompletos = useMemo(() => {
    if (!documentStatus || loadingDocumentos || !selectedSolicitud) return false;
    if (!documentosAdjuntos.length) return false;
    return documentosAdjuntos.every((doc) => !!documentStatus?.[doc.key]?.cargado);
  }, [documentStatus, loadingDocumentos, selectedSolicitud, documentosAdjuntos]);

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

  const obtenerHeadersAuditoriaUsuario = useCallback(() => {
    return buildUsernameHeader('X-Username');
  }, []);

  const cargarEstadoDocumentos = useCallback(async (tramiteId) => {
    setLoadingDocumentos(true);
    try {
      const response = await fetch(`${API_TRAMITES_URL}/${tramiteId}/verificar-documentos`, {
        headers: obtenerHeadersAuditoriaUsuario(),
      });
      if (!response.ok) throw new Error('No se pudo cargar el estado de documentos');
      const data = await response.json();
      setDocumentStatus(data);
    } catch {
      setDocumentStatus(null);
    } finally {
      setLoadingDocumentos(false);
    }
  }, [obtenerHeadersAuditoriaUsuario]);

  useEffect(() => {
    if (selectedSolicitud?.id) {
      cargarEstadoDocumentos(selectedSolicitud.id);
      return;
    }
    setDocumentStatus(null);
  }, [selectedSolicitud, cargarEstadoDocumentos]);

  const obtenerMensajeErrorRespuesta = async (response, mensajePorDefecto) => {
    try {
      const contentType = response.headers.get('content-type') || '';
      if (contentType.includes('application/json')) {
        const data = await response.json();
        const mensaje = data?.message || data?.error;
        if (mensaje) return mensaje;
      }

      const texto = (await response.text()).trim();
      if (texto) return texto;
    } catch {
      // no-op
    }
    return mensajePorDefecto;
  };

  const abrirDocumento = async (tramiteId, tipo) => {
    try {
      const response = await fetch(`${API_TRAMITES_URL}/${tramiteId}/descargar/${tipo}?accion=ver`, {
        headers: obtenerHeadersAuditoriaUsuario(),
      });
      if (!response.ok) throw new Error('Documento no disponible para abrir');
      const blob = await response.blob();
      const url = window.URL.createObjectURL(blob);
      window.open(url, '_blank', 'noopener,noreferrer');
    } catch (err) {
      mostrarAvisoPanel('error', `Error al abrir: ${err.message}`);
    }
  };

  const descargarDocumento = async (tramiteId, tipo) => {
    try {
      const response = await fetch(`${API_TRAMITES_URL}/${tramiteId}/descargar/${tipo}?accion=descargar`, {
        headers: obtenerHeadersAuditoriaUsuario(),
      });
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
      mostrarAvisoPanel('error', `Error al descargar: ${err.message}`);
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
      mostrarAvisoPanel('error', `Error al descargar consolidado: ${err.message}`);
    }
  };

  const handleAprobar = async () => {
    if (!selectedSolicitud) return;
    if (!documentosObligatoriosCompletos) {
      mostrarAvisoPanel('warning', 'No se puede aprobar: faltan documentos obligatorios por cargar.');
      return;
    }
    if (!consecutivo.trim()) {
      mostrarAvisoPanel('warning', 'Por favor, registra el consecutivo del verificador');
      return;
    }

    setProcesando(true);
    try {
      const user = getStoredUser();

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
      if (!response.ok) {
        const mensajeError = await obtenerMensajeErrorRespuesta(response, 'Error al aprobar');
        throw new Error(mensajeError);
      }

      mostrarAvisoPanel('success', 'Solicitud aprobada y enviada al alcalde para firma');
      setSelectedSolicitud(null);
      setObservaciones('');
      setConsecutivo('');
      await cargarSolicitudes();
    } catch (err) {
      mostrarAvisoPanel('error', `Error: ${err.message}`);
    } finally {
      setProcesando(false);
    }
  };

  const handleRechazar = async () => {
    if (!selectedSolicitud) return;
    if (!documentosObligatoriosCompletos) {
      mostrarAvisoPanel('warning', 'No se puede rechazar: faltan documentos obligatorios por cargar.');
      return;
    }
    if (!observaciones.trim()) {
      mostrarAvisoPanel('warning', 'Por favor, proporciona una razón para el rechazo');
      return;
    }
    if (!consecutivo.trim()) {
      mostrarAvisoPanel('warning', 'Por favor, registra el consecutivo del verificador');
      return;
    }

    setProcesando(true);
    try {
      const user = getStoredUser();

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
      if (!response.ok) {
        const mensajeError = await obtenerMensajeErrorRespuesta(response, 'Error al rechazar');
        throw new Error(mensajeError);
      }

      mostrarAvisoPanel('success', 'Solicitud rechazada. Se ha notificado al solicitante.');
      setSelectedSolicitud(null);
      setObservaciones('');
      setConsecutivo('');
      await cargarSolicitudes();
    } catch (err) {
      mostrarAvisoPanel('error', `Error: ${err.message}`);
    } finally {
      setProcesando(false);
    }
  };

  const handleNotificarAdmin = async () => {
    if (!selectedSolicitud) return;
    if (loadingDocumentos) {
      mostrarAvisoPanel('info', 'Espera a que cargue el estado documental para notificar al administrador.');
      return;
    }

    setEnviandoNotificacionAdmin(true);
    try {
      const user = getStoredUser();

      const mensajeBase = documentosFaltantes.length > 0
        ? `Se detectan documentos faltantes: ${documentosFaltantes.join(', ')}`
        : 'Solicito validación de consistencia entre documentos cargados y panel del verificador.';

      const response = await fetch(`${API_TRAMITES_URL}/${selectedSolicitud.id}/notificar-admin-documentos`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          username: user?.username || 'verificador',
          mensaje: mensajeBase,
        }),
      });

      if (!response.ok) {
        const errorBody = await response.text();
        throw new Error(errorBody || 'No se pudo enviar la notificación al administrador');
      }

      const data = await response.json();
      mostrarAvisoPanel('success', `Notificación enviada al administrador (destinatarios: ${data?.enviadoA || 0}).`);
    } catch (err) {
      mostrarAvisoPanel('error', `Error al notificar al administrador: ${err.message}`);
    } finally {
      setEnviandoNotificacionAdmin(false);
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
      <AvisoModal aviso={avisoPanel} onClose={() => setAvisoPanel(null)} />

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
                    <p style={styles.certMeta}>Fecha: {formatearFechaHora(cert.fechaVerificacion || cert.fechaFirmaAlcalde || cert.fechaRadicacion)}</p>
                  </div>
                ))}
              </div>
            )}
          </>
        ) : null}
      </div>

      <div style={{ ...styles.layout, ...(isMobile ? styles.layoutMobile : styles.layoutDesktop) }}>
        <div style={{ ...styles.lista, ...(isMobile ? {} : styles.listaDesktop) }}>
          <h3 style={styles.listaTitulo}>{filtroActual.titulo} ({solicitudesFiltradasBusqueda.length})</h3>

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
            <div style={styles.listaVacia}>No hay solicitudes para esta vista</div>
          ) : solicitudesFiltradasBusqueda.length === 0 ? (
            <div style={styles.listaVacia}>No hay solicitudes que coincidan con la búsqueda</div>
          ) : (
            <div style={{ ...styles.listaScroll, ...(isMobile ? styles.listaScrollMobile : styles.listaScrollDesktop) }}>
              {solicitudesFiltradasBusqueda.map((solicitud) => {
                const badge = estadoBadge(solicitud.estado);
                const esPendiente = solicitud.estado === 'EN_VALIDACION' || solicitud.estado === 'RADICADO';
                const textoDiasHabiles = esPendiente
                  ? obtenerTextoDiasHabilesRestantes(solicitud.fechaVencimiento)
                  : null;
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
                      Radicado: {formatearFechaHora(solicitud.fechaRadicacion)}
                    </p>
                    {textoDiasHabiles ? (
                      <p style={{ ...styles.itemP, color: textoDiasHabiles.startsWith('⚠️') ? '#b45309' : '#2563eb', fontSize: '12px', fontWeight: 600 }}>
                        {textoDiasHabiles}
                      </p>
                    ) : null}
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
                  <div style={styles.cardInfo}><span style={styles.label}>Fecha Radicación</span><p style={styles.value}>{formatearFechaHora(selectedSolicitud.fechaRadicacion)}</p></div>
                  {textoDiasHabilesSeleccionada ? <div style={styles.cardInfo}><span style={styles.label}>Tiempo restante</span><p style={styles.value}>{textoDiasHabilesSeleccionada}</p></div> : null}
                  <div style={styles.cardInfo}><span style={styles.label}>Consecutivo Documental</span><input style={styles.input} value={consecutivo} onChange={(e) => setConsecutivo(e.target.value.toUpperCase())} readOnly={!esPendienteSeleccionada} /></div>
                </div>
              </div>

              <div style={styles.seccion}>
                <h4 style={styles.h4}>📂 Documentos Adjuntos</h4>
                {hayDesfaseDocumentos ? (
                  <div style={styles.warning}>
                    ⚠️ Se detectó una inconsistencia: hay {documentStatus?.totalDocumentosCargados || 0} documento(s) cargado(s) pero solo se muestran {totalDocumentosMostrados}. Intenta recargar la solicitud y, si persiste, reporta este radicado.
                  </div>
                ) : null}
                {!loadingDocumentos && !documentosObligatoriosCompletos && esPendienteSeleccionada ? (
                  <div style={styles.warning}>
                    ⚠️ Faltan documentos obligatorios: {documentosFaltantes.join(', ')}. No podrás aprobar ni rechazar hasta que estén completos.
                  </div>
                ) : null}
                {documentStatus?.driveHabilitado ? (
                  <div style={{ ...styles.nota, marginTop: '0.5rem' }}>
                    Carpeta Drive del trámite: {documentStatus?.driveFolderId || 'No asignada aún'}
                  </div>
                ) : null}
                <div style={styles.docs}>
                  {documentosAdjuntos.map((doc) => {
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
                {esPendienteSeleccionada ? (
                  <div style={{ marginTop: '10px' }}>
                    <button
                      style={{ ...styles.btnNotificar, ...(enviandoNotificacionAdmin || loadingDocumentos ? styles.disabled : {}) }}
                      onClick={handleNotificarAdmin}
                      disabled={enviandoNotificacionAdmin || loadingDocumentos}
                    >
                      {enviandoNotificacionAdmin ? '⏳ Notificando administrador...' : '📩 Notificar al administrador para revisión documental'}
                    </button>
                  </div>
                ) : null}
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
                      <button style={{ ...styles.btnAprobar, ...(isMobile ? { width: '100%', minWidth: 0 } : {}), ...(procesando || observaciones.trim().length > 0 || !documentosObligatoriosCompletos ? styles.disabled : {}) }} onClick={handleAprobar} disabled={procesando || observaciones.trim().length > 0 || !documentosObligatoriosCompletos}>
                        {procesando ? '⏳ Procesando...' : '✅ Aprobar Solicitud'}
                      </button>
                      <button style={{ ...styles.btnRechazar, ...(isMobile ? { width: '100%', minWidth: 0 } : {}), ...(procesando || observaciones.trim().length === 0 || !documentosObligatoriosCompletos ? styles.disabled : {}) }} onClick={handleRechazar} disabled={procesando || observaciones.trim().length === 0 || !documentosObligatoriosCompletos}>
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