import { Fragment, useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { API_AUTH_URL, API_TRAMITES_URL } from '../config/api';
import { listarTramites, obtenerMetricasOperativasAdmin } from '../services/api';
import { formatearFecha as formatearFechaUtil, formatearFechaHora as formatearFechaHoraUtil } from '../utils/dateFormat';
import { filtrarCertificadosGenerados } from '../utils/certificateFilters';
import AvisoModal from './common/AvisoModal';

import { getPanelAdminStyles } from '../styles/components/PanelAdminStyles';
const styles = getPanelAdminStyles();

const getEstadoBadgeStyle = (estado) => {
  const key = (estado || '').toLowerCase();
  if (key === 'radicado') return { background: '#e3f2fd', color: '#1976d2' };
  if (key === 'en_validacion') return { background: '#fff3e0', color: '#f57c00' };
  if (key === 'en_firma') return { background: '#f3e5f5', color: '#7b1fa2' };
  if (key === 'finalizado') return { background: '#e8f5e9', color: '#388e3c' };
  return { background: '#ffebee', color: '#d32f2f' };
};

const formatoFecha = (valor) => formatearFechaUtil(valor, { fallback: '-' });

const formatoFechaHora = (valor) => formatearFechaHoraUtil(valor, { fallback: '-', incluirSegundos: true });

const formatoNumeroMetrica = (valor, decimales = 0) => {
  const numero = Number(valor);
  if (!Number.isFinite(numero)) return '-';
  return numero.toLocaleString('es-CO', {
    minimumFractionDigits: decimales,
    maximumFractionDigits: decimales,
  });
};

const formatoDuracion = (valorSegundos) => {
  const valor = Number(valorSegundos);
  if (!Number.isFinite(valor)) return '-';
  if (valor < 1) {
    return `${Math.round(valor * 1000)} ms`;
  }
  return `${valor.toFixed(2)} s`;
};

export default function PanelAdmin({ usuarioActual }) {
  const filasTramiteRef = useRef({});

  const [tramites, setTramites] = useState([]);
  const [loading, setLoading] = useState(false);
  const [usuariosOperativos, setUsuariosOperativos] = useState([]);
  const [usuariosExpandido, setUsuariosExpandido] = useState(false);
  const [loadingUsuarios, setLoadingUsuarios] = useState(false);
  const [guardandoUsuarioId, setGuardandoUsuarioId] = useState(null);
  const [tramiteExpandidoId, setTramiteExpandidoId] = useState(null);
  const [filtroCertRadicado, setFiltroCertRadicado] = useState('');
  const [filtroCertNombre, setFiltroCertNombre] = useState('');
  const [filtroCertTipo, setFiltroCertTipo] = useState('todos');
  const [certificadosExpandido, setCertificadosExpandido] = useState(false);
  const [documentStatusAdmin, setDocumentStatusAdmin] = useState({});
  const [loadingDocumentStatusAdminId, setLoadingDocumentStatusAdminId] = useState(null);
  const [auditoriaAdmin, setAuditoriaAdmin] = useState({});
  const [loadingAuditoriaAdminId, setLoadingAuditoriaAdminId] = useState(null);
  const [notificandoVerificadorId, setNotificandoVerificadorId] = useState(null);
  const [mensajeGestionAdmin, setMensajeGestionAdmin] = useState('');
  const [archivoCargaAdmin, setArchivoCargaAdmin] = useState({});
  const [subiendoDocumentoAdminKey, setSubiendoDocumentoAdminKey] = useState('');
  const [busquedaAdmin, setBusquedaAdmin] = useState('');
  const [avisoAdmin, setAvisoAdmin] = useState(null);
  const [metricasExpandido, setMetricasExpandido] = useState(false);
  const [intentoMetricasEnApertura, setIntentoMetricasEnApertura] = useState(false);
  const [loadingMetricas, setLoadingMetricas] = useState(false);
  const [errorMetricas, setErrorMetricas] = useState('');
  const [metricasOperativas, setMetricasOperativas] = useState(null);

  const mostrarAvisoAdmin = (tipo, mensaje) => {
    setAvisoAdmin({ tipo, mensaje });
  };

  const cargarTramites = useCallback(async ({ silenciosa = false, forceRefresh = false } = {}) => {
    if (!silenciosa) {
      setLoading(true);
    }
    try {
      const data = await listarTramites({ forceRefresh });
      setTramites(data);
    } catch (err) {
      console.error(err);
    } finally {
      if (!silenciosa) {
        setLoading(false);
      }
    }
  }, []);

  const cargarMetricasOperativas = useCallback(async () => {
    setLoadingMetricas(true);
    setErrorMetricas('');
    try {
      const data = await obtenerMetricasOperativasAdmin();
      setMetricasOperativas(data);
      if (data?.available === false) {
        setErrorMetricas('Metricas no disponibles: este backend no expone Actuator en /actuator.');
      }
    } catch (err) {
      setErrorMetricas(err?.message || 'No se pudieron cargar las métricas operativas');
    } finally {
      setLoadingMetricas(false);
    }
  }, []);

  const cargarUsuariosOperativos = useCallback(async () => {
    if (!usuarioActual?.username) return;
    setLoadingUsuarios(true);
    try {
      const response = await fetch(`${API_AUTH_URL}/usuarios-operativos`, {
        headers: { 'X-Admin-Username': usuarioActual.username },
      });
      if (!response.ok) {
        const msg = await response.text();
        throw new Error(msg || 'No se pudo cargar usuarios operativos');
      }
      const data = await response.json();
      setUsuariosOperativos(data.map((u) => ({
        ...u,
        editNombre: u.nombreCompleto || '',
        editUsername: u.username || '',
        editEmail: u.email || '',
      })));
    } catch (err) {
      console.error(err);
      mostrarAvisoAdmin('error', err.message || 'No se pudo cargar usuarios operativos');
    } finally {
      setLoadingUsuarios(false);
    }
  }, [usuarioActual?.username]);

  useEffect(() => {
    if (!usuarioActual?.username) return;
    cargarTramites();
  }, [usuarioActual?.username, cargarTramites]);

  useEffect(() => {
    if (!usuarioActual?.username) return;
    if (!usuariosExpandido) return;
    if (loadingUsuarios || usuariosOperativos.length > 0) return;
    cargarUsuariosOperativos();
  }, [usuarioActual?.username, usuariosExpandido, loadingUsuarios, usuariosOperativos.length, cargarUsuariosOperativos]);

  useEffect(() => {
    if (!usuarioActual?.username) return;
    if (!metricasExpandido || intentoMetricasEnApertura) return;
    setIntentoMetricasEnApertura(true);
    cargarMetricasOperativas();
  }, [usuarioActual?.username, metricasExpandido, intentoMetricasEnApertura, cargarMetricasOperativas]);

  useEffect(() => {
    if (metricasExpandido) return;
    setIntentoMetricasEnApertura(false);
  }, [metricasExpandido]);

  useEffect(() => {
    setMetricasExpandido(false);
    setIntentoMetricasEnApertura(false);
    setMetricasOperativas(null);
    setErrorMetricas('');
  }, [usuarioActual?.username]);

  useEffect(() => {
    if (!tramiteExpandidoId) return;

    const tramiteSigueVisible = tramites.some((tramite) => tramite.id === tramiteExpandidoId);
    if (!tramiteSigueVisible) {
      setTramiteExpandidoId(null);
      return;
    }

    const fila = filasTramiteRef.current[tramiteExpandidoId];
    if (!fila) return;

    const rect = fila.getBoundingClientRect();
    const estaEnPantalla = rect.top >= 0 && rect.bottom <= window.innerHeight;
    if (!estaEnPantalla) {
      fila.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
    }
  }, [tramites, tramiteExpandidoId]);

  const actualizarCampoUsuario = (id, campo, valor) => {
    setUsuariosOperativos((prev) => prev.map((u) => (u.id === id ? { ...u, [campo]: valor } : u)));
  };

  const guardarUsuarioOperativo = async (usuario) => {
    if (!usuarioActual?.username) return;
    const nombre = (usuario.editNombre || '').trim();
    const username = (usuario.editUsername || '').trim();
    const email = (usuario.editEmail || '').trim();
    if (!nombre) {
      mostrarAvisoAdmin('warning', 'El nombre completo es obligatorio');
      return;
    }
    if (!username) {
      mostrarAvisoAdmin('warning', 'El nombre de usuario es obligatorio');
      return;
    }
    if (!email) {
      mostrarAvisoAdmin('warning', 'El correo es obligatorio');
      return;
    }

    setGuardandoUsuarioId(usuario.id);
    try {
      const response = await fetch(`${API_AUTH_URL}/usuarios-operativos/${usuario.id}`, {
        method: 'PUT',
        headers: {
          'Content-Type': 'application/json',
          'X-Admin-Username': usuarioActual.username,
        },
        body: JSON.stringify({ nombreCompleto: nombre, username, email }),
      });
      if (!response.ok) {
        const msg = await response.text();
        throw new Error(msg || 'No se pudo actualizar el usuario');
      }

      setUsuariosOperativos((prev) => prev.map((u) => (
        u.id === usuario.id
          ? {
              ...u,
              nombreCompleto: nombre,
              username,
              email,
              editNombre: nombre,
              editUsername: username,
              editEmail: email,
            }
          : u
      )));
      mostrarAvisoAdmin('success', 'Usuario actualizado');
    } catch (err) {
      mostrarAvisoAdmin('error', err.message || 'No se pudo actualizar el usuario');
    } finally {
      setGuardandoUsuarioId(null);
    }
  };

  const certificadosGeneradosFiltrados = useMemo(() => filtrarCertificadosGenerados(tramites, {
    radicado: filtroCertRadicado,
    nombre: filtroCertNombre,
    tipo: filtroCertTipo,
  }), [tramites, filtroCertRadicado, filtroCertNombre, filtroCertTipo]);

  const textoBusquedaAdmin = busquedaAdmin.trim().toLowerCase();
  const tramitesFiltradosAdmin = useMemo(() => tramites.filter((tramite) => {
    if (!textoBusquedaAdmin) return true;
    const campos = [
      tramite?.numeroRadicado,
      tramite?.nombreSolicitante,
      tramite?.numeroDocumento,
      tramite?.estado,
      tramite?.tipoTramite,
      tramite?.tipo_certificado,
    ];
    return campos.some((valor) => (valor || '').toString().toLowerCase().includes(textoBusquedaAdmin));
  }), [tramites, textoBusquedaAdmin]);

  const resolverClaveCertificado = (tipoCertificado) => {
    const tipo = (tipoCertificado || '').toLowerCase();
    if (tipo === 'electoral') return 'electoral';
    if (tipo === 'jac') return 'residencia';
    return 'sisben';
  };

  const obtenerDocumentosAdmin = (tramite) => {
    const claveCertificado = resolverClaveCertificado(tramite?.tipo_certificado);
    return [
      { key: 'identidad', label: 'Documento de Identidad' },
      { key: 'solicitud', label: 'Documento de Solicitud' },
      { key: claveCertificado, label: `Certificado (${(tramite?.tipo_certificado || claveCertificado).toUpperCase()})` },
    ];
  };

  const obtenerFaltantesDocumentales = (tramiteId, documentosAdmin) => {
    const estado = documentStatusAdmin?.[tramiteId];
    if (!estado) return [];
    return documentosAdmin.filter((doc) => !estado?.[doc.key]?.cargado).map((doc) => doc.label);
  };

  const obtenerHeadersAuditoriaAdmin = () => {
    if (!usuarioActual?.username) return {};
    return { 'X-Username': usuarioActual.username };
  };

  const cargarEstadoDocumentosAdmin = async (tramiteId) => {
    setLoadingDocumentStatusAdminId(tramiteId);
    try {
      const response = await fetch(`${API_TRAMITES_URL}/${tramiteId}/verificar-documentos`, {
        headers: obtenerHeadersAuditoriaAdmin(),
      });
      if (!response.ok) throw new Error('No se pudo cargar el estado documental');
      const data = await response.json();
      setDocumentStatusAdmin((prev) => ({ ...prev, [tramiteId]: data }));
    } catch (err) {
      mostrarAvisoAdmin('error', err.message || 'No se pudo cargar el estado documental');
    } finally {
      setLoadingDocumentStatusAdminId(null);
    }
  };

  const cargarAuditoriaAdmin = async (tramiteId) => {
    if (!usuarioActual?.username) return;
    setLoadingAuditoriaAdminId(tramiteId);
    try {
      const response = await fetch(`${API_TRAMITES_URL}/${tramiteId}/auditoria`, {
        headers: { 'X-Admin-Username': usuarioActual.username },
      });
      if (!response.ok) {
        const msg = await response.text();
        throw new Error(msg || 'No se pudo cargar auditoría');
      }
      const data = await response.json();
      setAuditoriaAdmin((prev) => ({ ...prev, [tramiteId]: data?.eventos || [] }));
    } catch (err) {
      mostrarAvisoAdmin('error', err.message || 'No se pudo cargar auditoría');
    } finally {
      setLoadingAuditoriaAdminId(null);
    }
  };

  const abrirDocumentoAdmin = async (tramiteId, tipo) => {
    try {
      const response = await fetch(`${API_TRAMITES_URL}/${tramiteId}/descargar/${tipo}?accion=ver`, {
        headers: obtenerHeadersAuditoriaAdmin(),
      });
      if (!response.ok) throw new Error('Documento no disponible para abrir');
      const blob = await response.blob();
      const url = window.URL.createObjectURL(blob);
      window.open(url, '_blank', 'noopener,noreferrer');
      await cargarAuditoriaAdmin(tramiteId);
    } catch (err) {
      mostrarAvisoAdmin('error', err.message || 'No se pudo abrir el documento');
    }
  };

  const descargarDocumentoAdmin = async (tramiteId, tipo) => {
    try {
      const response = await fetch(`${API_TRAMITES_URL}/${tramiteId}/descargar/${tipo}?accion=descargar`, {
        headers: obtenerHeadersAuditoriaAdmin(),
      });
      if (!response.ok) throw new Error('Documento no disponible para descarga');
      const blob = await response.blob();
      const url = window.URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = `${tipo}_${tramiteId}`;
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      window.URL.revokeObjectURL(url);
      await cargarAuditoriaAdmin(tramiteId);
    } catch (err) {
      mostrarAvisoAdmin('error', err.message || 'No se pudo descargar el documento');
    }
  };

  const abrirDocumentoGeneradoAdmin = async (tramiteId) => {
    try {
      const response = await fetch(`${API_TRAMITES_URL}/${tramiteId}/documento-generado?accion=ver`, {
        headers: obtenerHeadersAuditoriaAdmin(),
      });
      if (!response.ok) throw new Error('Documento generado no disponible para abrir');
      const blob = await response.blob();
      const url = window.URL.createObjectURL(blob);
      window.open(url, '_blank', 'noopener,noreferrer');
      await cargarAuditoriaAdmin(tramiteId);
    } catch (err) {
      mostrarAvisoAdmin('error', err.message || 'No se pudo abrir el documento generado');
    }
  };

  const descargarDocumentoGeneradoAdmin = async (tramiteId, radicado) => {
    try {
      const response = await fetch(`${API_TRAMITES_URL}/${tramiteId}/documento-generado?accion=descargar`, {
        headers: obtenerHeadersAuditoriaAdmin(),
      });
      if (!response.ok) throw new Error('Documento generado no disponible para descarga');
      const blob = await response.blob();
      const url = window.URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = `certificado_generado_${radicado || tramiteId}.pdf`;
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      window.URL.revokeObjectURL(url);
      await cargarAuditoriaAdmin(tramiteId);
    } catch (err) {
      mostrarAvisoAdmin('error', err.message || 'No se pudo descargar el documento generado');
    }
  };

  const toggleDetalleTramiteAdmin = async (tramiteId) => {
    const nuevo = tramiteExpandidoId === tramiteId ? null : tramiteId;
    setTramiteExpandidoId(nuevo);
    if (nuevo) {
      const tareas = [];

      if (!documentStatusAdmin[tramiteId]) {
        tareas.push(cargarEstadoDocumentosAdmin(tramiteId));
      }

      tareas.push(cargarAuditoriaAdmin(tramiteId));
      await Promise.all(tareas);
    }
  };

  const notificarVerificadorDesdeAdmin = async (tramite) => {
    if (!usuarioActual?.username) return;

    const docs = obtenerDocumentosAdmin(tramite);
    const docsCompletos = docs.length > 0 && docs.every((doc) => !!documentStatusAdmin?.[tramite.id]?.[doc.key]?.cargado);
    if (!docsCompletos) {
      mostrarAvisoAdmin('warning', 'No puedes notificar aún: faltan documentos por cargar.');
      return;
    }

    setNotificandoVerificadorId(tramite.id);
    try {
      const response = await fetch(`${API_TRAMITES_URL}/${tramite.id}/notificar-verificador`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'X-Admin-Username': usuarioActual.username,
        },
        body: JSON.stringify({ mensaje: mensajeGestionAdmin.trim() }),
      });

      if (!response.ok) {
        const msg = await response.text();
        throw new Error(msg || 'No se pudo notificar al verificador');
      }

      const data = await response.json();
      mostrarAvisoAdmin('success', `Verificador(es) notificados: ${data.notificados || 0}`);
      setMensajeGestionAdmin('');
      await cargarAuditoriaAdmin(tramite.id);
    } catch (err) {
      mostrarAvisoAdmin('error', err.message || 'No se pudo notificar al verificador');
    } finally {
      setNotificandoVerificadorId(null);
    }
  };

  const seleccionarArchivoAdmin = (tramiteId, tipoDocumento, file) => {
    if (!file) return;
    const key = `${tramiteId}-${tipoDocumento}`;
    setArchivoCargaAdmin((prev) => ({ ...prev, [key]: file }));
  };

  const subirDocumentoAdmin = async (tramite, tipoDocumento) => {
    const key = `${tramite.id}-${tipoDocumento}`;
    const archivo = archivoCargaAdmin[key];
    if (!archivo) {
      mostrarAvisoAdmin('warning', 'Debes seleccionar un archivo antes de cargar.');
      return;
    }

    setSubiendoDocumentoAdminKey(key);
    try {
      const formData = new FormData();
      formData.append('file', archivo);

      const response = await fetch(`${API_TRAMITES_URL}/${tramite.id}/upload-${tipoDocumento}`, {
        method: 'POST',
        body: formData,
      });

      if (!response.ok) {
        const msg = await response.text();
        throw new Error(msg || 'No se pudo cargar el documento');
      }

      await cargarEstadoDocumentosAdmin(tramite.id);
      await cargarAuditoriaAdmin(tramite.id);
      setArchivoCargaAdmin((prev) => {
        const copy = { ...prev };
        delete copy[key];
        return copy;
      });
      mostrarAvisoAdmin('success', 'Documento cargado correctamente');
    } catch (err) {
      mostrarAvisoAdmin('error', err.message || 'No se pudo cargar el documento');
    } finally {
      setSubiendoDocumentoAdminKey('');
    }
  };

  if (!usuarioActual?.username) return null;

  return (
    <main style={styles.adminContenedor}>
      <AvisoModal aviso={avisoAdmin} onClose={() => setAvisoAdmin(null)} />

      <div style={styles.usuariosCard}>
        <div style={styles.seccionHeader}>
          <h2 style={{ ...styles.usuariosTitulo, marginBottom: 0 }}>Usuarios Operativos</h2>
          <button
            style={styles.btnToggleSeccion}
            onClick={() => setUsuariosExpandido((prev) => !prev)}
          >
            {usuariosExpandido ? 'Ocultar' : 'Mostrar'}
          </button>
        </div>

        {usuariosExpandido && loadingUsuarios ? <p>Cargando usuarios...</p> : null}
        {usuariosExpandido && !loadingUsuarios && usuariosOperativos.length === 0 ? <p>No hay usuarios operativos disponibles.</p> : null}
        {usuariosExpandido && !loadingUsuarios && usuariosOperativos.length > 0 ? (
          <div style={styles.usuariosGrid}>
            {usuariosOperativos.map((usuario) => (
              <div key={usuario.id} style={styles.usuarioItem}>
                <div style={styles.usuarioFila}>
                  <div>
                    <label style={styles.usuarioLabel}>Rol</label>
                    <input style={styles.usuarioInput} value={usuario.rol} readOnly />
                  </div>
                  <div>
                    <label style={styles.usuarioLabel}>Nombre Completo</label>
                    <input
                      style={styles.usuarioInput}
                      value={usuario.editNombre}
                      onChange={(e) => actualizarCampoUsuario(usuario.id, 'editNombre', e.target.value)}
                    />
                  </div>
                  <div>
                    <label style={styles.usuarioLabel}>Username</label>
                    <input
                      style={styles.usuarioInput}
                      value={usuario.editUsername}
                      onChange={(e) => actualizarCampoUsuario(usuario.id, 'editUsername', e.target.value)}
                    />
                  </div>
                  <div>
                    <label style={styles.usuarioLabel}>Correo</label>
                    <input
                      style={styles.usuarioInput}
                      type="email"
                      value={usuario.editEmail}
                      onChange={(e) => actualizarCampoUsuario(usuario.id, 'editEmail', e.target.value)}
                    />
                  </div>
                  <div>
                    <button
                      style={{ ...styles.btnGuardarUsuario, ...(guardandoUsuarioId === usuario.id ? styles.btnGuardarUsuarioDisabled : {}) }}
                      onClick={() => guardarUsuarioOperativo(usuario)}
                      disabled={guardandoUsuarioId === usuario.id}
                    >
                      {guardandoUsuarioId === usuario.id ? 'Guardando...' : 'Guardar'}
                    </button>
                  </div>
                </div>
                <div style={styles.usuarioMeta}>Estado: {usuario.activo ? 'Activo' : 'Inactivo'}</div>
              </div>
            ))}
          </div>
        ) : null}
      </div>

      <div style={styles.adminCard}>
        <div style={styles.seccionHeader}>
          <h2 style={{ ...styles.adminCardTitle, marginBottom: 0 }}>Metricas Operativas</h2>
          <div style={styles.metricasAcciones}>
            <button
              style={{ ...styles.btnRefrescar, ...(loadingMetricas ? styles.btnGuardarUsuarioDisabled : {}) }}
              onClick={cargarMetricasOperativas}
              disabled={loadingMetricas}
            >
              {loadingMetricas ? 'Actualizando...' : 'Refrescar'}
            </button>
            <button
              style={styles.btnToggleSeccion}
              onClick={() => setMetricasExpandido((prev) => !prev)}
            >
              {metricasExpandido ? 'Ocultar' : 'Mostrar'}
            </button>
          </div>
        </div>

        {metricasExpandido ? (
          <>
            <p style={styles.adminNota}>
              Fuente: Actuator. Ultima lectura: {metricasOperativas?.capturedAt ? formatoFechaHora(metricasOperativas.capturedAt) : '-'}
            </p>
            {errorMetricas ? <p style={styles.metricaError}>{errorMetricas}</p> : null}
            {loadingMetricas && !metricasOperativas ? <p>Cargando metricas...</p> : null}

            {metricasOperativas && metricasOperativas?.available !== false ? (
              <div style={styles.metricasGrid}>
                <div style={styles.metricaCard}>
                  <p style={styles.metricaTitulo}>Post-firma total</p>
                  <p style={styles.metricaValor}>{formatoNumeroMetrica(metricasOperativas?.postFirma?.total)}</p>
                  <p style={styles.metricaDetalle}>
                    OK: {formatoNumeroMetrica(metricasOperativas?.postFirma?.success)} | Exception: {formatoNumeroMetrica(metricasOperativas?.postFirma?.exception)}
                  </p>
                </div>

                <div style={styles.metricaCard}>
                  <p style={styles.metricaTitulo}>Errores de correo</p>
                  <p style={styles.metricaValor}>{formatoNumeroMetrica(metricasOperativas?.postFirma?.emailErrors)}</p>
                  <p style={styles.metricaDetalle}>Metrica: tramites.postfirma.email.errors</p>
                </div>

                <div style={styles.metricaCard}>
                  <p style={styles.metricaTitulo}>Duracion post-firma (OK)</p>
                  <p style={styles.metricaValor}>{formatoDuracion(metricasOperativas?.postFirma?.successAvgSeconds)}</p>
                  <p style={styles.metricaDetalle}>Maximo: {formatoDuracion(metricasOperativas?.postFirma?.successMaxSeconds)}</p>
                </div>

                <div style={styles.metricaCard}>
                  <p style={styles.metricaTitulo}>Generacion PDF total</p>
                  <p style={styles.metricaValor}>{formatoNumeroMetrica(metricasOperativas?.pdf?.total)}</p>
                  <p style={styles.metricaDetalle}>
                    OK: {formatoNumeroMetrica(metricasOperativas?.pdf?.success)} | Error: {formatoNumeroMetrica(metricasOperativas?.pdf?.errorTotal)}
                  </p>
                </div>

                <div style={styles.metricaCard}>
                  <p style={styles.metricaTitulo}>Motor PDF</p>
                  <p style={styles.metricaValor}>LO: {formatoNumeroMetrica(metricasOperativas?.pdf?.engineLibreoffice)}</p>
                  <p style={styles.metricaDetalle}>docx4j: {formatoNumeroMetrica(metricasOperativas?.pdf?.engineDocx4j)}</p>
                </div>

                <div style={styles.metricaCard}>
                  <p style={styles.metricaTitulo}>Duracion PDF</p>
                  <p style={styles.metricaValor}>{formatoDuracion(metricasOperativas?.pdf?.avgSeconds)}</p>
                  <p style={styles.metricaDetalle}>
                    Maximo: {formatoDuracion(metricasOperativas?.pdf?.maxSeconds)} | Errores: {formatoNumeroMetrica(metricasOperativas?.pdf?.errors)}
                  </p>
                </div>

                <div style={styles.metricaCard}>
                  <p style={styles.metricaTitulo}>HTTP p95 / p99</p>
                  <p style={styles.metricaValor}>{formatoDuracion(metricasOperativas?.http?.p95Seconds)}</p>
                  <p style={styles.metricaDetalle}>p99: {formatoDuracion(metricasOperativas?.http?.p99Seconds)}</p>
                </div>

                <div style={styles.metricaCard}>
                  <p style={styles.metricaTitulo}>HTTP total requests</p>
                  <p style={styles.metricaValor}>{formatoNumeroMetrica(metricasOperativas?.http?.count)}</p>
                  <p style={styles.metricaDetalle}>
                    Max: {formatoDuracion(metricasOperativas?.http?.maxSeconds)} | Total Time: {formatoDuracion(metricasOperativas?.http?.totalTimeSeconds)}
                  </p>
                </div>
              </div>
            ) : null}
          </>
        ) : null}
      </div>

      <div style={styles.adminCard}>
        <div style={styles.seccionHeader}>
          <h2 style={{ ...styles.adminCardTitle, marginBottom: 0 }}>Solicitudes Radicadas</h2>
          <button style={styles.btnRefrescar} onClick={() => cargarTramites({ forceRefresh: true })}>
            {loading ? 'Actualizando...' : 'Refrescar'}
          </button>
        </div>

        <div style={styles.adminBusquedaWrap}>
          <input
            style={styles.adminBusquedaInput}
            placeholder="Buscar por radicado, solicitante, documento, estado o tipo..."
            value={busquedaAdmin}
            onChange={(e) => setBusquedaAdmin(e.target.value)}
          />
          {busquedaAdmin.trim() ? (
            <button style={styles.btnToggleSeccion} onClick={() => setBusquedaAdmin('')}>
              Limpiar
            </button>
          ) : null}
          <p style={styles.adminBusquedaMeta}>Mostrando {tramitesFiltradosAdmin.length} de {tramites.length}</p>
        </div>

        {loading && tramites.length === 0 && <p>Cargando...</p>}
        {!loading && tramites.length === 0 && <p>No hay solicitudes registradas.</p>}
        {!loading && tramites.length > 0 && tramitesFiltradosAdmin.length === 0 ? <p>No hay solicitudes que coincidan con la búsqueda.</p> : null}
        {tramitesFiltradosAdmin.length > 0 ? (
          <div style={styles.tablaWrapper}>
            <table style={styles.tabla}>
              <thead>
                <tr>
                  <th style={{ ...styles.th, position: 'sticky', top: 0, zIndex: 2 }}>Radicado</th>
                  <th style={{ ...styles.th, position: 'sticky', top: 0, zIndex: 2 }}>Solicitante</th>
                  <th style={{ ...styles.th, position: 'sticky', top: 0, zIndex: 2 }}>Tipo</th>
                  <th style={{ ...styles.th, position: 'sticky', top: 0, zIndex: 2 }}>Estado</th>
                  <th style={{ ...styles.th, position: 'sticky', top: 0, zIndex: 2 }}>Fecha Radicación</th>
                  <th style={{ ...styles.th, position: 'sticky', top: 0, zIndex: 2 }}>Vencimiento</th>
                  <th style={{ ...styles.th, position: 'sticky', top: 0, zIndex: 2 }}>Acciones</th>
                </tr>
              </thead>
              <tbody>
                {tramitesFiltradosAdmin.map((tramite) => {
                  const expandido = tramiteExpandidoId === tramite.id;
                  const documentosAdmin = obtenerDocumentosAdmin(tramite);
                  const faltantesDocumentales = obtenerFaltantesDocumentales(tramite.id, documentosAdmin);
                  const auditoriaEventos = auditoriaAdmin?.[tramite.id] || [];
                  const estadoDocumental = documentStatusAdmin?.[tramite.id];
                  const certificadoGeneradoDisponible = !!estadoDocumental?.certificadoGeneradoDisponible;
                  const almacenamientoCertificadoGenerado = estadoDocumental?.certificadoGeneradoAlmacenamiento
                    || (tramite?.ruta_certificado_final?.startsWith('drive:') ? 'DRIVE' : 'BD');
                  const documentosCompletos = documentosAdmin.length > 0
                    && documentosAdmin.every((doc) => !!estadoDocumental?.[doc.key]?.cargado);
                  return (
                    <Fragment key={tramite.id}>
                      <tr ref={(elemento) => {
                        if (elemento) {
                          filasTramiteRef.current[tramite.id] = elemento;
                        } else {
                          delete filasTramiteRef.current[tramite.id];
                        }
                      }}>
                        <td style={{ ...styles.td, ...styles.celdaRadicado }}>{tramite.numeroRadicado}</td>
                        <td style={styles.td}>{tramite.nombreSolicitante}</td>
                        <td style={styles.td}>{tramite.tipoTramite}</td>
                        <td style={styles.td}>
                          <span style={{ ...styles.badge, ...getEstadoBadgeStyle(tramite.estado) }}>{tramite.estado}</span>
                          {documentStatusAdmin?.[tramite.id]
                            ? (
                              faltantesDocumentales.length > 0
                                ? <div style={styles.badgeDocsPendientes}>Faltan {faltantesDocumentales.length} documento(s)</div>
                                : <div style={styles.badgeDocsOk}>Documentos en regla</div>
                            )
                            : null}
                        </td>
                        <td style={styles.td}>{formatoFecha(tramite.fechaRadicacion)}</td>
                        <td style={styles.td}>{formatoFecha(tramite.fechaVencimiento)}</td>
                        <td style={styles.td}>
                          <button
                            style={styles.btnVer}
                            onClick={() => toggleDetalleTramiteAdmin(tramite.id)}
                          >
                            {expandido ? 'Ocultar' : 'Ver'}
                          </button>
                        </td>
                      </tr>
                      {expandido ? (
                        <tr key={`detalle-${tramite.id}`}>
                          <td colSpan={7} style={styles.td}>
                            <div style={styles.adminDetalle}>
                              <h3 style={styles.adminDetalleTitle}>Detalle de Solicitud</h3>
                              <div style={styles.adminDetalleGrid}>
                                <p style={styles.adminDetalleItem}><span style={styles.adminDetalleLabel}>Radicado:</span> {tramite.numeroRadicado || '-'}</p>
                                <p style={styles.adminDetalleItem}><span style={styles.adminDetalleLabel}>Solicitante:</span> {tramite.nombreSolicitante || '-'}</p>
                                <p style={styles.adminDetalleItem}><span style={styles.adminDetalleLabel}>Tipo:</span> {tramite.tipoTramite || '-'}</p>
                                <p style={styles.adminDetalleItem}><span style={styles.adminDetalleLabel}>Estado:</span> {tramite.estado || '-'}</p>
                                <p style={styles.adminDetalleItem}><span style={styles.adminDetalleLabel}>Documento:</span> {tramite.tipoDocumento || '-'} {tramite.numeroDocumento ? `- ${tramite.numeroDocumento}` : ''}</p>
                                <p style={styles.adminDetalleItem}><span style={styles.adminDetalleLabel}>Correo:</span> {tramite.correoElectronico || '-'}</p>
                                <p style={styles.adminDetalleItem}><span style={styles.adminDetalleLabel}>Dirección:</span> {tramite.direccionResidencia || '-'}</p>
                                <p style={styles.adminDetalleItem}><span style={styles.adminDetalleLabel}>Barrio:</span> {tramite.barrioResidencia || '-'}</p>
                                <p style={styles.adminDetalleItem}><span style={styles.adminDetalleLabel}>Fecha Radicación:</span> {formatoFecha(tramite.fechaRadicacion)}</p>
                                <p style={styles.adminDetalleItem}><span style={styles.adminDetalleLabel}>Fecha Vencimiento:</span> {formatoFecha(tramite.fechaVencimiento)}</p>
                                <p style={styles.adminDetalleItem}><span style={styles.adminDetalleLabel}>Fecha Vigencia:</span> {formatoFecha(tramite.fechaVigencia)}</p>
                                <p style={styles.adminDetalleItem}><span style={styles.adminDetalleLabel}>Observaciones:</span> {tramite.observaciones || '-'}</p>
                                <p style={styles.adminDetalleItem}><span style={styles.adminDetalleLabel}>Certificado generado:</span> {certificadoGeneradoDisponible ? 'Disponible' : 'Pendiente'}</p>
                                <p style={styles.adminDetalleItem}><span style={styles.adminDetalleLabel}>Almacenamiento certificado final:</span> {almacenamientoCertificadoGenerado}</p>
                                <p style={styles.adminDetalleItem}><span style={styles.adminDetalleLabel}>Drive habilitado:</span> {estadoDocumental?.driveHabilitado ? 'Sí' : 'No'}</p>
                                <p style={styles.adminDetalleItem}><span style={styles.adminDetalleLabel}>Carpeta Drive trámite:</span> {estadoDocumental?.driveFolderId || '-'}</p>
                              </div>

                              <div style={styles.adminDocs}>
                                <h4 style={{ margin: 0, color: '#1f2937' }}>Documentos del trámite</h4>
                                {loadingDocumentStatusAdminId === tramite.id ? <p style={styles.adminNota}>Cargando estado documental...</p> : null}
                                {!loadingDocumentStatusAdminId && documentStatusAdmin?.[tramite.id]
                                  ? (faltantesDocumentales.length > 0
                                    ? <p style={styles.faltantesTexto}>Faltantes detectados: {faltantesDocumentales.join(', ')}</p>
                                    : <p style={{ ...styles.faltantesTexto, color: '#166534' }}>Todos los documentos requeridos están cargados.</p>)
                                  : null}
                                {documentosAdmin.map((doc) => {
                                  const disponible = !!documentStatusAdmin?.[tramite.id]?.[doc.key]?.cargado;
                                  const claveArchivo = `${tramite.id}-${doc.key}`;
                                  const archivoSeleccionado = archivoCargaAdmin[claveArchivo];
                                  const subiendoEste = subiendoDocumentoAdminKey === claveArchivo;
                                  return (
                                    <div key={`${tramite.id}-${doc.key}`} style={styles.adminDocItem}>
                                      <span style={styles.adminDocLabel}>{doc.label}</span>
                                      <div style={styles.adminDocBtns}>
                                        {disponible ? (
                                          <>
                                            <button style={styles.btnDocVer} onClick={() => abrirDocumentoAdmin(tramite.id, doc.key)}>Ver</button>
                                            <button style={styles.btnDocDesc} onClick={() => descargarDocumentoAdmin(tramite.id, doc.key)}>Descargar</button>
                                          </>
                                        ) : (
                                          <span style={styles.adminNota}>No disponible</span>
                                        )}
                                        <input
                                          type="file"
                                          accept=".pdf,.jpg,.jpeg,.png,application/pdf,image/jpeg,image/png"
                                          onChange={(e) => seleccionarArchivoAdmin(tramite.id, doc.key, e.target.files?.[0])}
                                          style={{ maxWidth: '220px' }}
                                        />
                                        <button
                                          style={{ ...styles.btnDocDesc, ...(subiendoEste ? styles.btnGuardarUsuarioDisabled : {}) }}
                                          onClick={() => subirDocumentoAdmin(tramite, doc.key)}
                                          disabled={subiendoEste || !archivoSeleccionado}
                                        >
                                          {subiendoEste ? 'Subiendo...' : (disponible ? 'Reemplazar' : 'Cargar')}
                                        </button>
                                      </div>
                                    </div>
                                  );
                                })}

                                {(tramite.estado === 'FINALIZADO' || tramite.estado === 'RECHAZADO') ? (
                                  <div style={styles.adminDocItem}>
                                    <span style={styles.adminDocLabel}>Certificado generado (firmado)</span>
                                    <div style={styles.adminDocBtns}>
                                      <button style={styles.btnDocVer} onClick={() => abrirDocumentoGeneradoAdmin(tramite.id)}>Ver</button>
                                      <button style={styles.btnDocDesc} onClick={() => descargarDocumentoGeneradoAdmin(tramite.id, tramite.numeroRadicado)}>Descargar</button>
                                    </div>
                                  </div>
                                ) : null}

                                <input
                                  style={styles.inputAdminMensaje}
                                  placeholder="Mensaje para el verificador (opcional)"
                                  value={tramiteExpandidoId === tramite.id ? mensajeGestionAdmin : ''}
                                  onChange={(e) => setMensajeGestionAdmin(e.target.value)}
                                />
                                <button
                                  style={{ ...styles.btnNotificarVerificador, ...(notificandoVerificadorId === tramite.id ? styles.btnGuardarUsuarioDisabled : {}) }}
                                  onClick={() => notificarVerificadorDesdeAdmin(tramite)}
                                  disabled={notificandoVerificadorId === tramite.id || !documentosCompletos}
                                >
                                  {notificandoVerificadorId === tramite.id
                                    ? 'Notificando...'
                                    : (documentosCompletos
                                      ? '📩 Notificar verificador: documentos en regla'
                                      : '📩 Completa documentos para notificar')}
                                </button>
                                <p style={styles.adminNota}>
                                  {documentosCompletos
                                    ? 'Los documentos están en regla. Ya puedes notificar al verificador para continuar el trámite.'
                                    : 'Carga los documentos faltantes y luego notifica al verificador.'}
                                </p>

                                <div style={styles.adminAuditoria}>
                                  <h4 style={{ margin: 0, color: '#1f2937' }}>Trazabilidad del trámite (auditoría)</h4>
                                  {loadingAuditoriaAdminId === tramite.id ? <p style={styles.adminNota}>Cargando auditoría...</p> : null}
                                  {!loadingAuditoriaAdminId && auditoriaEventos.length === 0 ? <p style={styles.adminNota}>Aún no hay eventos de auditoría para este trámite.</p> : null}
                                  {!loadingAuditoriaAdminId && auditoriaEventos.length > 0 ? (
                                    <div style={styles.adminAuditoriaLista}>
                                      {auditoriaEventos.map((evento) => (
                                        <div key={`audit-${tramite.id}-${evento.id}`} style={styles.adminAuditoriaItem}>
                                          <p style={styles.adminAuditoriaAccion}>{evento.accion || 'EVENTO'}</p>
                                          <p style={styles.adminAuditoriaMeta}>Fecha: {formatoFechaHora(evento.fechaIntegracion)}</p>
                                          <p style={styles.adminAuditoriaMeta}>Usuario: {evento.username || 'sistema'}{evento.rol ? ` (${evento.rol})` : ''}</p>
                                          <p style={styles.adminAuditoriaMeta}>Estado: {evento.estadoAnterior || '-'} → {evento.estadoNuevo || '-'}</p>
                                          <p style={styles.adminAuditoriaMeta}>Detalle: {evento.descripcion || '-'}</p>
                                        </div>
                                      ))}
                                    </div>
                                  ) : null}
                                </div>
                              </div>
                            </div>
                          </td>
                        </tr>
                      ) : null}
                    </Fragment>
                  );
                })}
              </tbody>
            </table>
          </div>
        ) : null}
      </div>

      <div style={styles.adminCard}>
        <div style={styles.seccionHeader}>
          <h2 style={{ ...styles.adminCardTitle, marginBottom: 0 }}>Certificados Generados ({certificadosGeneradosFiltrados.length})</h2>
          <button
            style={styles.btnToggleSeccion}
            onClick={() => setCertificadosExpandido((prev) => !prev)}
          >
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
              <p>No hay certificados generados para los filtros seleccionados.</p>
            ) : (
              <div style={styles.listaCertificados}>
                {certificadosGeneradosFiltrados.map((cert) => (
                  <div key={`cert-admin-${cert.id}`} style={styles.certItem}>
                    <p style={styles.certMeta}><strong>{cert.numeroRadicado}</strong> · {cert.nombreSolicitante}</p>
                    <p style={styles.certMeta}>Respuesta: {cert.estado === 'FINALIZADO' ? 'Positiva' : 'Negativa'} · Tipo: {cert.tipo_certificado || cert.tipoTramite || 'Certificado'}</p>
                    <p style={styles.certMeta}>Fecha: {formatoFecha(cert.fechaVerificacion || cert.fechaFirmaAlcalde || cert.fechaRadicacion)}</p>
                  </div>
                ))}
              </div>
            )}
          </>
        ) : null}
      </div>
    </main>
  );
}