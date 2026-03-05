import { Component, Suspense, lazy, useCallback, useEffect, useMemo, useState } from 'react';
import { Navigate, Route, Routes, useLocation, useNavigate } from 'react-router-dom';
import { API_NOTIFICACIONES_URL } from './config/api';
import { formatearFechaHora as formatearFechaHoraUtil } from './utils/dateFormat';
import PanelConSesion from './components/PanelConSesion';
import CentroNotificacionesModal from './components/CentroNotificacionesModal';
import VistaInicio from './components/VistaInicio';
import VistaLogin from './components/VistaLogin';
import VistaRadicar from './components/VistaRadicar';
import VistaVerificar from './components/VistaVerificar';
import { getAppStyles } from './styles/components/AppStyles';

const PanelVerificador = lazy(() => import('./components/PanelVerificador'));
const PanelAlcalde = lazy(() => import('./components/PanelAlcalde'));
const PanelAdmin = lazy(() => import('./components/PanelAdmin'));

const RUTA_POR_VISTA = {
  inicio: '/',
  radicar: '/radicar',
  verificar: '/verificar',
  login: '/login',
  panel: '/panel',
};

const RUTA_PANEL_POR_ROL = {
  VERIFICADOR: '/panel/verificador',
  ALCALDE: '/panel/alcalde',
  ADMINISTRADOR: '/panel/admin',
};

const styles = getAppStyles();

function CargandoModulo({ texto = 'Cargando módulo...' }) {
  return <div style={styles.moduloLoading}>{texto}</div>;
}

class PanelErrorBoundary extends Component {
  constructor(props) {
    super(props);
    this.state = { hasError: false };
  }

  static getDerivedStateFromError() {
    return { hasError: true };
  }

  componentDidCatch(error) {
    console.error('Error en panel:', error);
  }

  render() {
    if (!this.state.hasError) {
      return this.props.children;
    }

    return (
      <div style={styles.moduloErrorWrap}>
        <div style={styles.moduloErrorCard}>
          <h3 style={styles.moduloErrorTitle}>No se pudo cargar este panel</h3>
          <p style={styles.moduloErrorText}>
            Ocurrió un error inesperado y se evitó que la aplicación quedara en blanco.
            Puedes volver a intentar o regresar al inicio.
          </p>
          <div style={styles.moduloErrorButtonRow}>
            <button
              type="button"
              style={styles.moduloErrorButton}
              onClick={this.props.onRetry}
            >
              Reintentar
            </button>
            <button
              type="button"
              style={styles.moduloErrorButton}
              onClick={this.props.onGoHome}
            >
              Ir al inicio
            </button>
          </div>
        </div>
      </div>
    );
  }
}

const obtenerUsuarioGuardado = () => {
  const raw = localStorage.getItem('usuario') || localStorage.getItem('user');
  if (!raw) return null;

  try {
    const user = JSON.parse(raw);
    return user?.rol ? user : null;
  } catch {
    return null;
  }
};

const rutaPanelPorRol = (rol) => RUTA_PANEL_POR_ROL[rol] || null;

const esRutaInterna = (ruta) => typeof ruta === 'string' && ruta.startsWith('/');

export default function App() {
  const navigate = useNavigate();
  const location = useLocation();

  const [sesionHidratada, setSesionHidratada] = useState(false);
  const [usuarioActual, setUsuarioActual] = useState(null);
  const [notificacionesUsuario, setNotificacionesUsuario] = useState([]);
  const [noLeidasUsuario, setNoLeidasUsuario] = useState(0);
  const [notificacionesAbiertas, setNotificacionesAbiertas] = useState(false);
  const [cargandoNotificaciones, setCargandoNotificaciones] = useState(false);

  useEffect(() => {
    const usuario = obtenerUsuarioGuardado();
    if (usuario) {
      setUsuarioActual(usuario);
    }
    setSesionHidratada(true);
  }, []);

  const limpiarSesionLocal = useCallback(() => {
    localStorage.removeItem('token');
    localStorage.removeItem('usuario');
    localStorage.removeItem('user');
  }, []);

  const cerrarSesion = useCallback(() => {
    setUsuarioActual(null);
    setNotificacionesAbiertas(false);
    setNotificacionesUsuario([]);
    setNoLeidasUsuario(0);
    limpiarSesionLocal();
    navigate('/', { replace: true });
  }, [limpiarSesionLocal, navigate]);

  const cambiarUsuario = useCallback(() => {
    const from = location.pathname.startsWith('/panel') ? location.pathname : '/panel';

    setUsuarioActual(null);
    setNotificacionesAbiertas(false);
    setNotificacionesUsuario([]);
    setNoLeidasUsuario(0);
    limpiarSesionLocal();
    navigate('/login', { state: { from }, replace: true });
  }, [limpiarSesionLocal, location.pathname, navigate]);

  const abrirVista = useCallback((vistaDestino) => {
    const rutaDestino = RUTA_POR_VISTA[vistaDestino] || '/';

    if (vistaDestino !== 'panel') {
      navigate(rutaDestino);
      return;
    }

    if (!usuarioActual) {
      navigate('/login', { state: { from: '/panel' } });
      return;
    }

    const rutaPanel = rutaPanelPorRol(usuarioActual.rol) || '/panel';
    navigate(rutaPanel);
  }, [navigate, usuarioActual]);

  const handleLoginUnificado = useCallback((usuario) => {
    const from = location.state?.from;
    const rutaRol = rutaPanelPorRol(usuario?.rol) || '/panel';
    const destino = esRutaInterna(from) ? from : rutaRol;

    setUsuarioActual(usuario);
    localStorage.setItem('usuario', JSON.stringify(usuario));
    localStorage.setItem('user', JSON.stringify(usuario));
    navigate(destino, { replace: true });
  }, [location.state, navigate]);

  const formatearFechaHora = useCallback((valor) => formatearFechaHoraUtil(valor, {
    fallback: '-',
    incluirSegundos: true,
  }), []);

  const enPanel = location.pathname.startsWith('/panel');

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

  const marcarNotificacionLeida = useCallback(async (idNotificacion) => {
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
  }, [obtenerHeadersNotificaciones]);

  const marcarTodasNotificacionesLeidas = useCallback(async () => {
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
  }, [obtenerHeadersNotificaciones]);

  useEffect(() => {
    if (!(enPanel && usuarioActual?.username)) {
      return;
    }

    cargarNotificacionesUsuario({ silenciosa: true });

    const intervalo = setInterval(() => {
      cargarNotificacionesUsuario({ silenciosa: true });
    }, 15000);

    return () => clearInterval(intervalo);
  }, [cargarNotificacionesUsuario, enPanel, usuarioActual?.username]);

  useEffect(() => {
    if (!(enPanel && usuarioActual?.username)) {
      setNotificacionesAbiertas(false);
      setNotificacionesUsuario([]);
      setNoLeidasUsuario(0);
    }
  }, [enPanel, usuarioActual?.username]);

  const centroNotificaciones = useMemo(() => (
    <CentroNotificacionesModal
      abierto={notificacionesAbiertas}
      cargando={cargandoNotificaciones}
      notificaciones={notificacionesUsuario}
      onCerrar={() => setNotificacionesAbiertas(false)}
      onMarcarTodas={marcarTodasNotificacionesLeidas}
      onMarcarUna={marcarNotificacionLeida}
      formatearFechaHora={formatearFechaHora}
    />
  ), [
    cargandoNotificaciones,
    formatearFechaHora,
    marcarNotificacionLeida,
    marcarTodasNotificacionesLeidas,
    notificacionesAbiertas,
    notificacionesUsuario,
  ]);

  if (!sesionHidratada) {
    return <CargandoModulo texto="Cargando aplicación..." />;
  }

  const renderPanelRuta = ({ rolEsperado, contenido, etiquetaRol, titulo, esAdmin = false }) => {
    if (!usuarioActual) {
      return <Navigate to="/login" state={{ from: location.pathname }} replace />;
    }

    if (usuarioActual.rol !== rolEsperado) {
      const rutaRol = rutaPanelPorRol(usuarioActual.rol) || '/';
      return <Navigate to={rutaRol} replace />;
    }

    return (
      <PanelConSesion
        esAdmin={esAdmin}
        usuarioActual={usuarioActual}
        titulo={titulo}
        etiquetaRol={etiquetaRol}
        noLeidasUsuario={noLeidasUsuario}
        onVolver={() => navigate('/')}
        onAbrirCentroNotificaciones={abrirCentroNotificaciones}
        onCambiarUsuario={cambiarUsuario}
        onCerrarSesion={cerrarSesion}
      >
        {contenido}
        {centroNotificaciones}
      </PanelConSesion>
    );
  };

  return (
    <Routes>
      <Route path="/" element={<VistaInicio onAbrirVista={abrirVista} />} />
      <Route path="/radicar" element={<VistaRadicar onVolver={() => navigate('/')} onIrAVerificar={() => navigate('/verificar')} />} />
      <Route path="/verificar" element={<VistaVerificar onVolver={() => navigate('/')} />} />
      <Route
        path="/login"
        element={usuarioActual
          ? <Navigate to={rutaPanelPorRol(usuarioActual.rol) || '/'} replace />
          : <VistaLogin onVolver={() => navigate('/')} onLoginSuccess={handleLoginUnificado} />}
      />

      <Route
        path="/panel"
        element={usuarioActual
          ? <Navigate to={rutaPanelPorRol(usuarioActual.rol) || '/'} replace />
          : <Navigate to="/login" state={{ from: '/panel' }} replace />}
      />

      <Route
        path="/panel/verificador"
        element={renderPanelRuta({
          rolEsperado: 'VERIFICADOR',
          etiquetaRol: '👤 Verificador',
          contenido: (
            <PanelErrorBoundary onRetry={() => navigate(0)} onGoHome={() => navigate('/')}>
              <Suspense fallback={<CargandoModulo texto="Cargando panel de verificador..." />}>
                <PanelVerificador />
              </Suspense>
            </PanelErrorBoundary>
          ),
        })}
      />

      <Route
        path="/panel/alcalde"
        element={renderPanelRuta({
          rolEsperado: 'ALCALDE',
          etiquetaRol: '👨‍⚖️ Alcalde',
          contenido: (
            <PanelErrorBoundary onRetry={() => navigate(0)} onGoHome={() => navigate('/')}>
              <Suspense fallback={<CargandoModulo texto="Cargando panel de alcalde..." />}>
                <PanelAlcalde />
              </Suspense>
            </PanelErrorBoundary>
          ),
        })}
      />

      <Route
        path="/panel/admin"
        element={renderPanelRuta({
          rolEsperado: 'ADMINISTRADOR',
          esAdmin: true,
          titulo: 'Panel Administrativo',
          etiquetaRol: '⚙️ Administrador',
          contenido: (
            <PanelErrorBoundary onRetry={() => navigate(0)} onGoHome={() => navigate('/')}>
              <Suspense fallback={<CargandoModulo texto="Cargando panel administrativo..." />}>
                <PanelAdmin usuarioActual={usuarioActual} />
              </Suspense>
            </PanelErrorBoundary>
          ),
        })}
      />

      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
