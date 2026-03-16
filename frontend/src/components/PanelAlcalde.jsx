import { useEffect, useMemo, useState } from 'react';
import { API_TRAMITES_URL } from '../config/api';
import { listarTramites } from '../services/api';
import { formatearFechaHora as formatearFechaHoraUtil } from '../utils/dateFormat';
import { buildUsernameHeader, getStoredUser } from '../utils/userSession';
import { filtrarCertificadosGenerados } from '../utils/certificateFilters';
import AvisoModal from './common/AvisoModal';

import { getPanelAlcaldeStyles } from '../styles/components/PanelAlcaldeStyles';
const FILTROS = [
  { key: 'pendientes', label: 'Pendientes', titulo: '📑 Certificados Pendientes de Firma', estado: 'EN_FIRMA' },
  { key: 'aprobadas', label: 'Aprobadas', titulo: '✅ Solicitudes Aprobadas', estado: 'FINALIZADO' },
  { key: 'negadas', label: 'Negadas', titulo: '❌ Solicitudes Negadas', estado: 'RECHAZADO' },
];

const styles = getPanelAlcaldeStyles();

const getEstadoBadge = (solicitud) => {
  const estado = solicitud?.estado;
  if (estado === 'EN_FIRMA') {
    if (solicitud?.verificacionAprobada === false) return '🔖 Para Firmar (Negada)';
    return '🔖 Para Firmar (Aprobada)';
  }
  if (estado === 'FINALIZADO') return '✅ Aprobada';
  return '❌ Negada';
};

const formatearFecha = (valor) => formatearFechaHoraUtil(valor, { fallback: 'Sin fecha' });

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
  const [isMobile, setIsMobile] = useState(typeof window !== 'undefined' ? window.innerWidth < 768 : false);
  const [filtroCertRadicado, setFiltroCertRadicado] = useState('');
  const [filtroCertNombre, setFiltroCertNombre] = useState('');
  const [filtroCertTipo, setFiltroCertTipo] = useState('todos');
  const [certificadosExpandido, setCertificadosExpandido] = useState(false);
  const [avisoPanel, setAvisoPanel] = useState(null);

  const mostrarAvisoPanel = (tipo, mensaje) => {
    setAvisoPanel({ tipo, mensaje });
  };

  useEffect(() => {
    cargarSolicitudes();
  }, []);

  useEffect(() => {
    const onResize = () => setIsMobile(window.innerWidth < 768);
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

  const certificadosGeneradosFiltrados = useMemo(() => filtrarCertificadosGenerados(solicitudes, {
    radicado: filtroCertRadicado,
    nombre: filtroCertNombre,
    tipo: filtroCertTipo,
  }), [solicitudes, filtroCertRadicado, filtroCertNombre, filtroCertTipo]);

  const esPendienteFirma = selectedSolicitud?.estado === 'EN_FIRMA';

  const cargarSolicitudes = async ({ forceRefresh = false } = {}) => {
    try {
      const data = await listarTramites({ forceRefresh });
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
      const headers = buildUsernameHeader('X-Username');

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
      const user = getStoredUser();

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
      await cargarSolicitudes({ forceRefresh: true });
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
        <p style={styles.subtitulo}>Firma los certificados de residencia aprobados o negados por verificador</p>
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

      <AvisoModal aviso={avisoPanel} onClose={() => setAvisoPanel(null)} />

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
              name="busquedaSolicitudesAlcalde"
              style={styles.busquedaInput}
              placeholder="Buscar por radicado, solicitante, documento, estado o tipo..."
              value={busquedaSolicitudes}
              autoComplete="new-password"
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
                    <span style={styles.badge}>{getEstadoBadge(solicitud)}</span>
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