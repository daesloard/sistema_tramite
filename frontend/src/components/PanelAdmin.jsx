import { Fragment, useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { API_AUTH_URL, API_TRAMITES_URL } from '../config/api';
import { listarTramites } from '../services/api';

const styles = {
  adminContenedor: { maxWidth: '1200px', margin: '0 auto', padding: 'clamp(0.9rem, 4vw, 2rem)' },
  adminCard: { background: '#fff', borderRadius: '8px', padding: 'clamp(1rem, 4vw, 2rem)', boxShadow: '0 2px 8px rgba(0,0,0,0.1)' },
  adminCardTitle: { color: '#333', marginBottom: '1.5rem' },
  adminBusquedaWrap: { display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '12px', flexWrap: 'wrap' },
  adminBusquedaInput: { width: 'min(520px, 100%)', border: '1px solid #cfd8dc', borderRadius: '6px', padding: '8px 10px', fontSize: '13px', boxSizing: 'border-box' },
  adminBusquedaMeta: { margin: 0, fontSize: '12px', color: '#6b7280' },
  adminDetalle: { border: '1px solid #e5e7eb', borderRadius: '8px', background: '#f9fafb', padding: '0.8rem' },
  adminDetalleTitle: { margin: '0 0 0.8rem 0', color: '#1f2937', fontSize: '1rem' },
  adminDetalleGrid: { display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: '0.6rem 1rem' },
  adminDetalleItem: { margin: 0, color: '#374151', fontSize: '0.92rem' },
  adminDetalleLabel: { fontWeight: 700, color: '#111827' },
  tablaWrapper: { overflowX: 'auto', overflowY: 'auto', maxHeight: '420px' },
  tabla: { width: '100%', borderCollapse: 'collapse' },
  th: { padding: '0.75rem', textAlign: 'left', fontWeight: 600, color: '#333', borderBottom: '2px solid #ddd', background: 'linear-gradient(135deg, #667eea15, #764ba215)' },
  td: { padding: '0.75rem', borderBottom: '1px solid #eee', color: '#666' },
  celdaRadicado: { fontFamily: 'Courier New, monospace', fontWeight: 'bold', color: '#667eea', letterSpacing: '0.5px' },
  badge: { display: 'inline-block', padding: '0.4rem 0.8rem', borderRadius: '12px', fontSize: '0.85rem', fontWeight: 600 },
  btnVer: { padding: '0.5rem 1rem', background: '#667eea', color: '#fff', border: 'none', borderRadius: '4px', cursor: 'pointer', fontWeight: 600 },
  usuariosCard: { background: '#fff', borderRadius: '8px', padding: 'clamp(1rem, 4vw, 2rem)', boxShadow: '0 2px 8px rgba(0,0,0,0.1)', marginBottom: '1rem' },
  usuariosTitulo: { color: '#333', marginBottom: '1rem' },
  usuariosGrid: { display: 'grid', gap: '0.75rem' },
  usuarioItem: { border: '1px solid #e5e7eb', borderRadius: '8px', padding: '0.75rem', background: '#fafafa' },
  usuarioFila: { display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: '0.6rem', alignItems: 'end' },
  usuarioLabel: { display: 'block', fontSize: '12px', color: '#6b7280', marginBottom: '4px', fontWeight: 600 },
  usuarioInput: { width: '100%', padding: '8px 10px', border: '1px solid #cfd8dc', borderRadius: '6px', fontSize: '14px', boxSizing: 'border-box' },
  usuarioMeta: { fontSize: '12px', color: '#4b5563', marginTop: '6px' },
  btnGuardarUsuario: { padding: '8px 12px', border: 'none', borderRadius: '6px', background: '#2563eb', color: '#fff', cursor: 'pointer', fontWeight: 600 },
  btnGuardarUsuarioDisabled: { background: '#9ca3af', cursor: 'not-allowed' },
  seccionHeader: { display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: '0.8rem', marginBottom: '1rem', flexWrap: 'wrap' },
  btnToggleSeccion: { padding: '0.35rem 0.75rem', background: '#4f46e5', color: '#fff', border: 'none', borderRadius: '6px', cursor: 'pointer', fontWeight: 600, fontSize: '0.78rem', width: 'auto', whiteSpace: 'nowrap' },
  btnRefrescar: { padding: '0.35rem 0.75rem', background: '#0f766e', color: '#fff', border: 'none', borderRadius: '6px', cursor: 'pointer', fontWeight: 600, fontSize: '0.78rem', width: 'auto', whiteSpace: 'nowrap' },
  filtrosCert: { display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: '8px', marginBottom: '10px' },
  inputFiltro: { width: '100%', border: '1px solid #cfd8dc', borderRadius: '6px', padding: '8px 10px', fontSize: '13px', boxSizing: 'border-box' },
  listaCertificados: { display: 'grid', gap: '8px', maxHeight: '220px', overflowY: 'auto' },
  certItem: { border: '1px solid #e5e7eb', borderRadius: '8px', padding: '8px 10px', background: '#f9fafb' },
  certMeta: { margin: '2px 0', fontSize: '12px', color: '#4b5563' },
  adminDocs: { marginTop: '0.9rem', display: 'flex', flexDirection: 'column', gap: '8px' },
  adminDocItem: { display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: '10px', padding: '8px 10px', border: '1px solid #e5e7eb', borderRadius: '8px', background: '#fff' },
  adminDocLabel: { color: '#1f2937', fontSize: '0.9rem', fontWeight: 600 },
  adminDocBtns: { display: 'flex', gap: '8px' },
  btnDocVer: { padding: '6px 10px', border: 'none', borderRadius: '6px', background: '#2563eb', color: '#fff', cursor: 'pointer', fontWeight: 600, fontSize: '12px' },
  btnDocDesc: { padding: '6px 10px', border: 'none', borderRadius: '6px', background: '#059669', color: '#fff', cursor: 'pointer', fontWeight: 600, fontSize: '12px' },
  btnNotificarVerificador: { padding: '8px 12px', border: 'none', borderRadius: '6px', background: '#d97706', color: '#fff', cursor: 'pointer', fontWeight: 600, marginTop: '10px' },
  inputAdminMensaje: { width: '100%', border: '1px solid #cfd8dc', borderRadius: '6px', padding: '8px 10px', fontSize: '13px', boxSizing: 'border-box', marginTop: '10px' },
  adminNota: { marginTop: '8px', fontSize: '12px', color: '#6b7280' },
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
  adminAuditoria: { marginTop: '1rem', borderTop: '1px dashed #d1d5db', paddingTop: '0.9rem' },
  adminAuditoriaLista: { display: 'grid', gap: '8px', maxHeight: '220px', overflowY: 'auto' },
  adminAuditoriaItem: { border: '1px solid #e5e7eb', borderRadius: '8px', background: '#fff', padding: '8px 10px' },
  adminAuditoriaMeta: { margin: '2px 0', fontSize: '12px', color: '#374151' },
  adminAuditoriaAccion: { margin: '0 0 4px 0', fontSize: '12px', fontWeight: 700, color: '#111827' },
  badgeDocsPendientes: { display: 'inline-block', marginTop: '6px', padding: '4px 8px', borderRadius: '999px', background: '#fff3e0', color: '#b45309', fontSize: '11px', fontWeight: 700 },
  badgeDocsOk: { display: 'inline-block', marginTop: '6px', padding: '4px 8px', borderRadius: '999px', background: '#e8f5e9', color: '#15803d', fontSize: '11px', fontWeight: 700 },
  faltantesTexto: { margin: '6px 0 0 0', fontSize: '12px', color: '#92400e' },
};

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
  if (Number.isNaN(fecha.getTime())) return '-';
  return fecha.toLocaleDateString('es-CO');
};

const formatoFechaHora = (valor) => {
  if (!valor) return '-';
  const fecha = new Date(valor);
  if (Number.isNaN(fecha.getTime())) return '-';
  return fecha.toLocaleString('es-CO', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  });
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

  const mostrarAvisoAdmin = (tipo, mensaje) => {
    setAvisoAdmin({ tipo, mensaje });
  };

  const obtenerEstiloAvisoAdmin = (tipo) => {
    if (tipo === 'success') return styles.panelAvisoExito;
    if (tipo === 'warning') return styles.panelAvisoWarning;
    if (tipo === 'info') return styles.panelAvisoInfo;
    return styles.panelAvisoError;
  };

  const cargarTramites = useCallback(async ({ silenciosa = false } = {}) => {
    if (!silenciosa) {
      setLoading(true);
    }
    try {
      const data = await listarTramites();
      setTramites(data);
    } catch (err) {
      console.error(err);
    } finally {
      if (!silenciosa) {
        setLoading(false);
      }
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

  const certificadosGeneradosFiltrados = useMemo(() => tramites
    .filter((t) => t.estado === 'FINALIZADO' || t.estado === 'RECHAZADO')
    .filter((t) => {
      const matchRadicado = (t.numeroRadicado || '').toLowerCase().includes(filtroCertRadicado.toLowerCase().trim());
      const matchNombre = (t.nombreSolicitante || '').toLowerCase().includes(filtroCertNombre.toLowerCase().trim());
      const matchTipo = filtroCertTipo === 'todos'
        || (filtroCertTipo === 'positiva' && t.estado === 'FINALIZADO')
        || (filtroCertTipo === 'negativa' && t.estado === 'RECHAZADO');
      return matchRadicado && matchNombre && matchTipo;
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
      if (!documentStatusAdmin[tramiteId]) {
        await cargarEstadoDocumentosAdmin(tramiteId);
      }
      await cargarAuditoriaAdmin(tramiteId);
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
      {avisoAdmin ? (
        <div style={styles.avisoOverlay} onClick={() => setAvisoAdmin(null)}>
          <div style={{ ...styles.panelAviso, ...obtenerEstiloAvisoAdmin(avisoAdmin.tipo) }} onClick={(e) => e.stopPropagation()}>
            <p style={styles.panelAvisoTexto}>{avisoAdmin.mensaje}</p>
            <div style={styles.panelAvisoAcciones}>
              <button style={styles.panelAvisoCerrar} onClick={() => setAvisoAdmin(null)} aria-label="Cerrar aviso">Entendido</button>
            </div>
          </div>
        </div>
      ) : null}

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
          <h2 style={{ ...styles.adminCardTitle, marginBottom: 0 }}>Solicitudes Radicadas</h2>
          <button style={styles.btnRefrescar} onClick={() => cargarTramites()}>
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
