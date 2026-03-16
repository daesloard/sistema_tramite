import { Fragment, useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { API_AUTH_URL, API_TRAMITES_URL, API_DOCUMENTOS_URL } from '../config/api';
import { listarTramites, obtenerMetricasOperativasAdmin } from '../services/api';
import { filtrarCertificadosGenerados } from '../utils/certificateFilters';
import { formatearFechaHora } from '../utils/dateUtils';
import AvisoModal from './common/AvisoModal';

// Nuevos sub-componentes
import AdminMetrics from './admin/AdminMetrics';
import AdminUserManagement from './admin/AdminUserManagement';
import AdminTramiteList from './admin/AdminTramiteList';
import AdminCertificates from './admin/AdminCertificates';

import { getPanelAdminStyles } from '../styles/components/PanelAdminStyles';
const styles = getPanelAdminStyles();

// Funciones utilitarias locales
const getEstadoBadgeStyle = (estado) => {
  const key = (estado || '').toLowerCase();
  if (key === 'radicado') return { background: '#e3f2fd', color: '#1976d2' };
  if (key === 'en_validacion') return { background: '#fff3e0', color: '#f57c00' };
  if (key === 'en_firma') return { background: '#f3e5f5', color: '#7b1fa2' };
  if (key === 'finalizado') return { background: '#e8f5e9', color: '#388e3c' };
  return { background: '#ffebee', color: '#d32f2f' };
};

const formatoFecha = (valor) => {
  if (!valor) return '-';
  const fecha = new Date(valor);
  if (isNaN(fecha.getTime())) return '-';
  return fecha.toLocaleDateString('es-CO');
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
    if (!silenciosa) setLoading(true);
    try {
      const data = await listarTramites({ forceRefresh });
      setTramites(data);
    } catch (err) {
      console.error(err);
    } finally {
      if (!silenciosa) setLoading(false);
    }
  }, []);

  const cargarMetricasOperativas = useCallback(async () => {
    setLoadingMetricas(true);
    setErrorMetricas('');
    try {
      const data = await obtenerMetricasOperativasAdmin();
      setMetricasOperativas(data);
      if (data?.available === false) {
        setErrorMetricas('Metricas no disponibles: este backend no expone Actuator o no hay conectividad.');
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
    if (!usuarioActual?.username || !usuariosExpandido) return;
    if (loadingUsuarios || usuariosOperativos.length > 0) return;
    cargarUsuariosOperativos();
  }, [usuarioActual?.username, usuariosExpandido, loadingUsuarios, usuariosOperativos.length, cargarUsuariosOperativos]);

  useEffect(() => {
    if (!usuarioActual?.username || !metricasExpandido || intentoMetricasEnApertura) return;
    setIntentoMetricasEnApertura(true);
    cargarMetricasOperativas();
  }, [usuarioActual?.username, metricasExpandido, intentoMetricasEnApertura, cargarMetricasOperativas]);

  useEffect(() => {
    if (!metricasExpandido) setIntentoMetricasEnApertura(false);
  }, [metricasExpandido]);

  useEffect(() => {
    if (!tramiteExpandidoId) return;
    const tramiteSigueVisible = tramites.some((t) => t.id === tramiteExpandidoId);
    if (!tramiteSigueVisible) {
      setTramiteExpandidoId(null);
      return;
    }
    const fila = filasTramiteRef.current[tramiteExpandidoId];
    if (fila) {
      const rect = fila.getBoundingClientRect();
      const estaEnPantalla = rect.top >= 0 && rect.bottom <= window.innerHeight;
      if (!estaEnPantalla) fila.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
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
    if (!nombre || !username || !email) {
      mostrarAvisoAdmin('warning', 'Todos los campos son obligatorios');
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
      if (!response.ok) throw new Error(await response.text() || 'No se pudo actualizar el usuario');

      setUsuariosOperativos((prev) => prev.map((u) => u.id === usuario.id ? { ...u, nombreCompleto: nombre, username, email, editNombre: nombre, editUsername: username, editEmail: email } : u));
      mostrarAvisoAdmin('success', 'Usuario actualizado');
    } catch (err) {
      mostrarAvisoAdmin('error', err.message);
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
  const tramitesFiltradosAdmin = useMemo(() => tramites.filter((t) => {
    if (!textoBusquedaAdmin) return true;
    const campos = [t?.numeroRadicado, t?.nombreSolicitante, t?.numeroDocumento, t?.estado, t?.tipoTramite];
    return campos.some((v) => (v || '').toString().toLowerCase().includes(textoBusquedaAdmin));
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

  const cargarEstadoDocumentosAdmin = async (tramiteId) => {
    setLoadingDocumentStatusAdminId(tramiteId);
    try {
      const response = await fetch(`${API_DOCUMENTOS_URL}/verificar/${tramiteId}`, {
        headers: { 'X-Username': usuarioActual.username },
      });
      if (!response.ok) {
        const errorData = await response.text();
        throw new Error(errorData || 'Error al cargar estado documental');
      }
      const data = await response.json();
      setDocumentStatusAdmin((prev) => ({ ...prev, [tramiteId]: data }));
    } catch (err) {
      mostrarAvisoAdmin('error', err.message);
    } finally {
      setLoadingDocumentStatusAdminId(null);
    }
  };

  const cargarAuditoriaAdmin = async (tramiteId) => {
    setLoadingAuditoriaAdminId(tramiteId);
    try {
      const response = await fetch(`${API_TRAMITES_URL}/${tramiteId}/auditoria`, {
        headers: { 'X-Admin-Username': usuarioActual.username },
      });
      if (!response.ok) throw new Error('Error al cargar auditoría');
      const data = await response.json();
      setAuditoriaAdmin((prev) => ({ ...prev, [tramiteId]: data?.eventos || [] }));
    } catch (err) {
      mostrarAvisoAdmin('error', err.message);
    } finally {
      setLoadingAuditoriaAdminId(null);
    }
  };

  const abrirDocumentoAdmin = async (tramiteId, tipo) => {
    try {
      const response = await fetch(`${API_DOCUMENTOS_URL}/descargar/${tramiteId}?tipo=${tipo}&accion=ver`, {
        headers: { 'X-Username': usuarioActual.username },
      });
      if (!response.ok) throw new Error('Documento no disponible');
      const blob = await response.blob();
      window.open(window.URL.createObjectURL(blob), '_blank');
      await cargarAuditoriaAdmin(tramiteId);
    } catch (err) {
      mostrarAvisoAdmin('error', err.message);
    }
  };

  const descargarDocumentoAdmin = async (tramiteId, tipo) => {
    try {
      const response = await fetch(`${API_DOCUMENTOS_URL}/descargar/${tramiteId}?tipo=${tipo}&accion=descargar`, {
        headers: { 'X-Username': usuarioActual.username },
      });
      if (!response.ok) throw new Error('Error al descargar');
      const blob = await response.blob();
      const link = document.createElement('a');
      link.href = window.URL.createObjectURL(blob);
      link.download = `${tipo}_${tramiteId}`;
      link.click();
      await cargarAuditoriaAdmin(tramiteId);
    } catch (err) {
      mostrarAvisoAdmin('error', err.message);
    }
  };

  const toggleDetalleTramiteAdmin = async (tramiteId) => {
    const nuevo = tramiteExpandidoId === tramiteId ? null : tramiteId;
    setTramiteExpandidoId(nuevo);
    if (nuevo) {
      await Promise.all([
        !documentStatusAdmin[tramiteId] ? cargarEstadoDocumentosAdmin(tramiteId) : Promise.resolve(),
        cargarAuditoriaAdmin(tramiteId)
      ]);
    }
  };

  const seleccionarArchivoAdmin = (tramiteId, tipoDocumento, file) => {
    if (!file) return;
    setArchivoCargaAdmin((prev) => ({ ...prev, [`${tramiteId}-${tipoDocumento}`]: file }));
  };

  const subirDocumentoAdmin = async (tramite, tipoDocumento) => {
    const key = `${tramite.id}-${tipoDocumento}`;
    const archivo = archivoCargaAdmin[key];
    if (!archivo) return mostrarAvisoAdmin('warning', 'Selecciona un archivo');
    setSubiendoDocumentoAdminKey(key);
    try {
      const formData = new FormData();
      formData.append('file', archivo);
      const response = await fetch(`${API_DOCUMENTOS_URL}/subir/${tramite.id}/${tipoDocumento}`, { 
        method: 'POST', 
        body: formData,
        headers: { 'X-Username': usuarioActual.username }
      });
      if (!response.ok) throw new Error(await response.text() || 'Error al cargar');
      await Promise.all([cargarEstadoDocumentosAdmin(tramite.id), cargarAuditoriaAdmin(tramite.id)]);
      setArchivoCargaAdmin((prev) => { const c = { ...prev }; delete c[key]; return c; });
      mostrarAvisoAdmin('success', 'Archivo cargado');
    } catch (err) {
      mostrarAvisoAdmin('error', err.message);
    } finally {
      setSubiendoDocumentoAdminKey('');
    }
  };

  const notificarVerificadorDesdeAdmin = async (tramite) => {
    setNotificandoVerificadorId(tramite.id);
    try {
      const response = await fetch(`${API_TRAMITES_URL}/${tramite.id}/notificar-verificador`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'X-Admin-Username': usuarioActual.username },
        body: JSON.stringify({ mensaje: mensajeGestionAdmin.trim() }),
      });
      if (!response.ok) throw new Error(await response.text() || 'Error al notificar');
      mostrarAvisoAdmin('success', 'Verificador notificado');
      setMensajeGestionAdmin('');
      await cargarAuditoriaAdmin(tramite.id);
    } catch (err) {
      mostrarAvisoAdmin('error', err.message);
    } finally {
      setNotificandoVerificadorId(null);
    }
  };

  const abrirDocumentoGeneradoAdmin = async (tramiteId) => {
    try {
      const response = await fetch(`${API_TRAMITES_URL}/${tramiteId}/documento-generado?accion=ver`, { headers: { 'X-Username': usuarioActual.username } });
      if (!response.ok) throw new Error('No disponible');
      const blob = await response.blob();
      window.open(window.URL.createObjectURL(blob), '_blank');
      await cargarAuditoriaAdmin(tramiteId);
    } catch (err) {
      mostrarAvisoAdmin('error', err.message);
    }
  };

  const descargarDocumentoGeneradoAdmin = async (tramiteId, radicado) => {
    try {
      const response = await fetch(`${API_TRAMITES_URL}/${tramiteId}/documento-generado?accion=descargar`, { headers: { 'X-Username': usuarioActual.username } });
      if (!response.ok) throw new Error('Error al descargar');
      const blob = await response.blob();
      const link = document.createElement('a');
      link.href = window.URL.createObjectURL(blob);
      link.download = `certificado_${radicado || tramiteId}.pdf`;
      link.click();
      await cargarAuditoriaAdmin(tramiteId);
    } catch (err) {
      mostrarAvisoAdmin('error', err.message);
    }
  };

  if (!usuarioActual?.username) return null;

  return (
    <main style={styles.adminContenedor}>
      <AvisoModal aviso={avisoAdmin} onClose={() => setAvisoAdmin(null)} />

      <AdminUserManagement
        usuariosOperativos={usuariosOperativos}
        loadingUsuarios={loadingUsuarios}
        usuariosExpandido={usuariosExpandido}
        setUsuariosExpandido={setUsuariosExpandido}
        guardandoUsuarioId={guardandoUsuarioId}
        actualizarCampoUsuario={actualizarCampoUsuario}
        guardarUsuarioOperativo={guardarUsuarioOperativo}
        styles={styles}
      />

      <AdminMetrics
        metricasOperativas={metricasOperativas}
        loadingMetricas={loadingMetricas}
        errorMetricas={errorMetricas}
        cargarMetricasOperativas={cargarMetricasOperativas}
        expandido={metricasExpandido}
        setExpandido={setMetricasExpandido}
        styles={styles}
      />

      <AdminTramiteList
        tramites={tramites}
        tramitesFiltradosAdmin={tramitesFiltradosAdmin}
        loading={loading}
        cargarTramites={cargarTramites}
        busquedaAdmin={busquedaAdmin}
        setBusquedaAdmin={setBusquedaAdmin}
        tramiteExpandidoId={tramiteExpandidoId}
        toggleDetalleTramiteAdmin={toggleDetalleTramiteAdmin}
        filasTramiteRef={filasTramiteRef}
        documentStatusAdmin={documentStatusAdmin}
        auditoriaAdmin={auditoriaAdmin}
        loadingDocumentStatusAdminId={loadingDocumentStatusAdminId}
        loadingAuditoriaAdminId={loadingAuditoriaAdminId}
        archivoCargaAdmin={archivoCargaAdmin}
        subiendoDocumentoAdminKey={subiendoDocumentoAdminKey}
        mensajeGestionAdmin={mensajeGestionAdmin}
        setMensajeGestionAdmin={setMensajeGestionAdmin}
        seleccionarArchivoAdmin={seleccionarArchivoAdmin}
        subirDocumentoAdmin={subirDocumentoAdmin}
        abrirDocumentoAdmin={abrirDocumentoAdmin}
        descargarDocumentoAdmin={descargarDocumentoAdmin}
        abrirDocumentoGeneradoAdmin={abrirDocumentoGeneradoAdmin}
        descargarDocumentoGeneradoAdmin={descargarDocumentoGeneradoAdmin}
        notificarVerificadorDesdeAdmin={notificarVerificadorDesdeAdmin}
        notificandoVerificadorId={notificandoVerificadorId}
        obtenerDocumentosAdmin={obtenerDocumentosAdmin}
        obtenerFaltantesDocumentales={obtenerFaltantesDocumentales}
        getEstadoBadgeStyle={getEstadoBadgeStyle}
        formatoFecha={formatoFecha}
        formatoFechaHora={formatearFechaHora}
        styles={styles}
      />

      <AdminCertificates
        certificadosExpandido={certificadosExpandido}
        setCertificadosExpandido={setCertificadosExpandido}
        filtroCertRadicado={filtroCertRadicado}
        setFiltroCertRadicado={setFiltroCertRadicado}
        filtroCertNombre={filtroCertNombre}
        setFiltroCertNombre={setFiltroCertNombre}
        filtroCertTipo={filtroCertTipo}
        setFiltroCertTipo={setFiltroCertTipo}
        certificadosGeneradosFiltrados={certificadosGeneradosFiltrados}
        formatoFecha={formatoFecha}
        styles={styles}
      />
    </main>
  );
}