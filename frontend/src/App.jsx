import { Suspense, lazy, useCallback, useEffect, useState } from 'react';
import { API_NOTIFICACIONES_URL } from './config/api';
import PanelConSesion from './components/PanelConSesion';
import CentroNotificacionesModal from './components/CentroNotificacionesModal';
import VistaInicio from './components/VistaInicio';
import VistaLogin from './components/VistaLogin';
import VistaRadicar from './components/VistaRadicar';
import VistaVerificar from './components/VistaVerificar';

const PanelVerificador = lazy(() => import('./components/PanelVerificador'));
const PanelAlcalde = lazy(() => import('./components/PanelAlcalde'));
const PanelAdmin = lazy(() => import('./components/PanelAdmin'));

const VISTA_STORAGE_KEY = 'sistema_tramites_vista';
const VISTAS_VALIDAS = ['inicio', 'radicar', 'verificar', 'login', 'panel'];

const styles = {
  moduloLoading: {
    minHeight: '40vh',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    color: '#4b5563',
    fontSize: '1rem',
    fontWeight: 600,
    padding: '1rem',
  },
};

function CargandoModulo({ texto = 'Cargando módulo...' }) {
  return <div style={styles.moduloLoading}>{texto}</div>;
}

export default function App() {
  const [vista, setVista] = useState('inicio');
  const [vistaHidratada, setVistaHidratada] = useState(false);
  const [usuarioActual, setUsuarioActual] = useState(null);
  const [vistaLoginDestino, setVistaLoginDestino] = useState(null);
  const [notificacionesUsuario, setNotificacionesUsuario] = useState([]);
  const [noLeidasUsuario, setNoLeidasUsuario] = useState(0);
  const [notificacionesAbiertas, setNotificacionesAbiertas] = useState(false);
  const [cargandoNotificaciones, setCargandoNotificaciones] = useState(false);

  useEffect(() => {
    const vistaGuardada = localStorage.getItem(VISTA_STORAGE_KEY);
    if (vistaGuardada && VISTAS_VALIDAS.includes(vistaGuardada)) {
      setVista(vistaGuardada);
    }

    const usuarioGuardado = localStorage.getItem('usuario') || localStorage.getItem('user');
    if (!usuarioGuardado) return;

    try {
      const usuarioParseado = JSON.parse(usuarioGuardado);
      if (usuarioParseado?.rol) {
        setUsuarioActual(usuarioParseado);
      }
    } catch (error) {
      console.warn('No se pudo restaurar la sesión del usuario', error);
    }

    setVistaHidratada(true);
  }, []);

  useEffect(() => {
    if (!vistaHidratada) return;
    localStorage.setItem(VISTA_STORAGE_KEY, vista);
  }, [vista, vistaHidratada]);

  const cerrarSesion = () => {
    setUsuarioActual(null);
    setVistaLoginDestino(null);
    setNotificacionesAbiertas(false);
    setNotificacionesUsuario([]);
    setNoLeidasUsuario(0);
    localStorage.removeItem('token');
    localStorage.removeItem('usuario');
    localStorage.removeItem('user');
    setVista('inicio');
  };

  const cambiarUsuario = () => {
    if (esVistaProtegida(vista)) {
      setVistaLoginDestino(vista);
    }
    setUsuarioActual(null);
    setNotificacionesAbiertas(false);
    setNotificacionesUsuario([]);
    setNoLeidasUsuario(0);
    localStorage.removeItem('token');
    localStorage.removeItem('usuario');
    localStorage.removeItem('user');
    setVista('login');
  };

  const esVistaProtegida = (vistaDestino) => vistaDestino === 'panel';

  const abrirVista = (vistaDestino) => {
    if (!esVistaProtegida(vistaDestino)) {
      setVista(vistaDestino);
      return;
    }

    if (usuarioActual) {
      setVista(vistaDestino);
      return;
    }

    setVistaLoginDestino(vistaDestino);
    setVista('login');
  };

  const handleLoginUnificado = (usuario) => {
    const destino = vistaLoginDestino || (esVistaProtegida(vista) ? vista : 'panel');

    setUsuarioActual(usuario);
    localStorage.setItem('usuario', JSON.stringify(usuario));
    localStorage.setItem('user', JSON.stringify(usuario));
    setVista(destino);
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

  const obtenerHeadersNotificaciones = useCallback(() => {
    if (!usuarioActual?.username) return {};
    return { 'X-Username': usuarioActual.username };
  }, [usuarioActual?.username]);

  const cargarNotificacionesUsuario = useCallback(async ({ silenciosa = false } = {}) => {
    if (!usuarioActual?.username) {
      setNotificacionesUsuario([]);
      setNoLeidasUsuario(0);
      return;
    }

    if (!silenciosa) {
      setCargandoNotificaciones(true);
    }

    try {
      const response = await fetch(`${API_NOTIFICACIONES_URL}/mis`, {
        headers: obtenerHeadersNotificaciones(),
      });
      if (!response.ok) {
        throw new Error('No se pudo cargar el centro de notificaciones');
      }
      const data = await response.json();
      setNotificacionesUsuario(Array.isArray(data?.notificaciones) ? data.notificaciones : []);
      setNoLeidasUsuario(Number(data?.noLeidas || 0));
    } catch (err) {
      if (!silenciosa) {
        console.error(err.message || 'No se pudieron cargar notificaciones');
      }
    } finally {
      if (!silenciosa) {
        setCargandoNotificaciones(false);
      }
    }
  }, [usuarioActual?.username, obtenerHeadersNotificaciones]);

  const abrirCentroNotificaciones = async () => {
    setNotificacionesAbiertas(true);
    await cargarNotificacionesUsuario();
  };

  const marcarNotificacionLeida = async (idNotificacion) => {
    if (!idNotificacion) return;
    try {
      const response = await fetch(`${API_NOTIFICACIONES_URL}/${idNotificacion}/leer`, {
        method: 'POST',
        headers: obtenerHeadersNotificaciones(),
      });
      if (!response.ok) {
        throw new Error('No se pudo marcar la notificación como leída');
      }
      setNotificacionesUsuario((prev) => prev.map((item) => (
        item.id === idNotificacion ? { ...item, leida: true } : item
      )));
      setNoLeidasUsuario((prev) => Math.max(0, prev - 1));
    } catch (err) {
      console.error(err.message || 'Error al marcar notificación');
    }
  };

  const marcarTodasNotificacionesLeidas = async () => {
    try {
      const response = await fetch(`${API_NOTIFICACIONES_URL}/leer-todas`, {
        method: 'POST',
        headers: obtenerHeadersNotificaciones(),
      });
      if (!response.ok) {
        throw new Error('No se pudieron marcar todas las notificaciones');
      }
      setNotificacionesUsuario((prev) => prev.map((item) => ({ ...item, leida: true })));
      setNoLeidasUsuario(0);
    } catch (err) {
      console.error(err.message || 'Error al marcar notificaciones');
    }
  };

  useEffect(() => {
    if (!(vista === 'panel' && usuarioActual?.username)) {
      return;
    }

    cargarNotificacionesUsuario({ silenciosa: true });

    const intervalo = setInterval(() => {
      cargarNotificacionesUsuario({ silenciosa: true });
    }, 15000);

    return () => clearInterval(intervalo);
  }, [vista, usuarioActual?.username, cargarNotificacionesUsuario]);

  useEffect(() => {
    if (!(vista === 'panel' && usuarioActual?.username)) {
      setNotificacionesAbiertas(false);
      setNotificacionesUsuario([]);
      setNoLeidasUsuario(0);
    }
  }, [vista, usuarioActual?.username]);

  const centroNotificaciones = (
    <CentroNotificacionesModal
      abierto={notificacionesAbiertas}
      cargando={cargandoNotificaciones}
      notificaciones={notificacionesUsuario}
      onCerrar={() => setNotificacionesAbiertas(false)}
      onMarcarTodas={marcarTodasNotificacionesLeidas}
      onMarcarUna={marcarNotificacionLeida}
      formatearFechaHora={formatoFechaHora}
    />
  );

  if (vista === 'inicio') {
    return <VistaInicio onAbrirVista={abrirVista} />;
  }

  if (vista === 'radicar') {
    return <VistaRadicar onVolver={() => setVista('inicio')} onIrAVerificar={() => setVista('verificar')} />;
  }

  if (vista === 'verificar') {
    return <VistaVerificar onVolver={() => setVista('inicio')} />;
  }

  if (vista === 'login') {
    return <VistaLogin onVolver={() => setVista('inicio')} onLoginSuccess={handleLoginUnificado} />;
  }

  if (vista === 'panel') {
    if (!usuarioActual) {
      return <VistaLogin onVolver={() => setVista('inicio')} onLoginSuccess={handleLoginUnificado} />;
    }

    if (usuarioActual.rol === 'VERIFICADOR') {
      return (
        <PanelConSesion
          usuarioActual={usuarioActual}
          etiquetaRol="👤 Verificador"
          noLeidasUsuario={noLeidasUsuario}
          onVolver={() => setVista('inicio')}
          onAbrirCentroNotificaciones={abrirCentroNotificaciones}
          onCambiarUsuario={cambiarUsuario}
          onCerrarSesion={cerrarSesion}
        >
          <Suspense fallback={<CargandoModulo texto="Cargando panel de verificador..." />}>
            <PanelVerificador />
          </Suspense>
          {centroNotificaciones}
        </PanelConSesion>
      );
    }

    if (usuarioActual.rol === 'ALCALDE') {
      return (
        <PanelConSesion
          usuarioActual={usuarioActual}
          etiquetaRol="👨‍⚖️ Alcalde"
          noLeidasUsuario={noLeidasUsuario}
          onVolver={() => setVista('inicio')}
          onAbrirCentroNotificaciones={abrirCentroNotificaciones}
          onCambiarUsuario={cambiarUsuario}
          onCerrarSesion={cerrarSesion}
        >
          <Suspense fallback={<CargandoModulo texto="Cargando panel de alcalde..." />}>
            <PanelAlcalde />
          </Suspense>
          {centroNotificaciones}
        </PanelConSesion>
      );
    }

    return (
      <PanelConSesion
        esAdmin
        usuarioActual={usuarioActual}
        titulo="Panel Administrativo"
        etiquetaRol="⚙️ Administrador"
        noLeidasUsuario={noLeidasUsuario}
        onVolver={() => setVista('inicio')}
        onAbrirCentroNotificaciones={abrirCentroNotificaciones}
        onCambiarUsuario={cambiarUsuario}
        onCerrarSesion={cerrarSesion}
      >
        <Suspense fallback={<CargandoModulo texto="Cargando panel administrativo..." />}>
          <PanelAdmin usuarioActual={usuarioActual} />
        </Suspense>
        {centroNotificaciones}
      </PanelConSesion>
    );
  }

  return null;
}
