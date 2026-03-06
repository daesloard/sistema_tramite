import { Component, Suspense, lazy, useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Navigate, Route, Routes, useLocation, useNavigate } from 'react-router-dom';
import { API_NOTIFICACIONES_URL } from './config/api';
import { desactivarWebPushSuscripcion, obtenerWebPushPublicKey, registrarWebPushSuscripcion } from './services/api';
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

const POLL_NOTIFICACIONES_CERRADO_MS = 60000;
const POLL_NOTIFICACIONES_ABIERTO_MS = 20000;
const MIN_GAP_CARGA_NOTIFICACIONES_MS = 8000;

const soportaNotificacionesNavegador = () => (
  typeof window !== 'undefined' && 'Notification' in window
);

const soportaWebPushNativo = () => (
  typeof window !== 'undefined'
  && typeof navigator !== 'undefined'
  && 'serviceWorker' in navigator
  && 'PushManager' in window
);

const urlBase64ToUint8Array = (base64String) => {
  const padding = '='.repeat((4 - (base64String.length % 4)) % 4);
  const base64 = (base64String + padding)
    .replace(/-/g, '+')
    .replace(/_/g, '/');

  const rawData = window.atob(base64);
  const outputArray = new Uint8Array(rawData.length);

  for (let i = 0; i < rawData.length; i += 1) {
    outputArray[i] = rawData.charCodeAt(i);
  }

  return outputArray;
};

const keyPreferenciaNotificacionesNavegador = (username) => `notificaciones_navegador_${username || 'anon'}`;

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
  const [notificacionesNavegadorHabilitadas, setNotificacionesNavegadorHabilitadas] = useState(false);
  const [webPushSuscrito, setWebPushSuscrito] = useState(false);
  const notificacionesEnVueloRef = useRef(false);
  const ultimaCargaNotificacionesRef = useRef(0);
  const trackingNotificacionesRef = useRef({ username: null, initialized: false, seenIds: new Set() });

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

  useEffect(() => {
    if (!soportaWebPushNativo()) {
      return undefined;
    }

    const onWorkerMessage = (event) => {
      if (event?.data?.type === 'OPEN_NOTIFICATIONS_CENTER') {
        setNotificacionesAbiertas(true);
      }
    };

    navigator.serviceWorker.addEventListener('message', onWorkerMessage);
    return () => navigator.serviceWorker.removeEventListener('message', onWorkerMessage);
  }, []);

  useEffect(() => {
    if (!soportaNotificacionesNavegador() || !usuarioActual?.username) {
      setNotificacionesNavegadorHabilitadas(false);
      setWebPushSuscrito(false);
      trackingNotificacionesRef.current = { username: usuarioActual?.username || null, initialized: false, seenIds: new Set() };
      return;
    }

    const key = keyPreferenciaNotificacionesNavegador(usuarioActual.username);
    const preferenciaGuardada = localStorage.getItem(key) === 'true';
    const permisoConcedido = Notification.permission === 'granted';

    setNotificacionesNavegadorHabilitadas(preferenciaGuardada && permisoConcedido);
    trackingNotificacionesRef.current = { username: usuarioActual.username, initialized: false, seenIds: new Set() };
  }, [usuarioActual?.username]);

  const lanzarNotificacionNavegador = useCallback((notificacion) => {
    if (!soportaNotificacionesNavegador() || Notification.permission !== 'granted') return;

    const titulo = (notificacion?.titulo || 'Nueva notificación').toString();
    const mensaje = (notificacion?.mensaje || 'Tienes una actualización en el sistema').toString();
    const id = notificacion?.id || Date.now();

    const instancia = new Notification(titulo, {
      body: mensaje,
      tag: `tramite-noti-${id}`,
      icon: '/escudo.png',
      badge: '/escudo.png',
    });

    instancia.onclick = () => {
      if (typeof window !== 'undefined') {
        window.focus();
      }
      setNotificacionesAbiertas(true);
    };
  }, []);

  const procesarNotificacionesNavegador = useCallback((listaNotificaciones) => {
    if (!notificacionesNavegadorHabilitadas || !usuarioActual?.username) return;
    if (!soportaNotificacionesNavegador() || Notification.permission !== 'granted') return;
    if (webPushSuscrito) return;

    const tracking = trackingNotificacionesRef.current;
    if (tracking.username !== usuarioActual.username) {
      trackingNotificacionesRef.current = {
        username: usuarioActual.username,
        initialized: false,
        seenIds: new Set(),
      };
    }

    const actual = trackingNotificacionesRef.current;
    const noLeidas = (Array.isArray(listaNotificaciones) ? listaNotificaciones : [])
      .filter((item) => !item?.leida && item?.id != null);

    if (!actual.initialized) {
      noLeidas.forEach((item) => actual.seenIds.add(item.id));
      actual.initialized = true;
      return;
    }

    const nuevas = noLeidas.filter((item) => !actual.seenIds.has(item.id));
    nuevas.forEach((item) => {
      actual.seenIds.add(item.id);
      lanzarNotificacionNavegador(item);
    });
  }, [lanzarNotificacionNavegador, notificacionesNavegadorHabilitadas, usuarioActual?.username, webPushSuscrito]);

  const cargarNotificacionesUsuario = useCallback(async ({ silenciosa = false, forzar = false } = {}) => {
    if (!usuarioActual?.username) {
      setNotificacionesUsuario([]);
      setNoLeidasUsuario(0);
      return;
    }

    const ahora = Date.now();
    const ultimaCarga = ultimaCargaNotificacionesRef.current;
    if (!forzar && (ahora - ultimaCarga) < MIN_GAP_CARGA_NOTIFICACIONES_MS) {
      return;
    }

    if (notificacionesEnVueloRef.current) {
      return;
    }

    notificacionesEnVueloRef.current = true;
    ultimaCargaNotificacionesRef.current = ahora;

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
      const lista = Array.isArray(data?.notificaciones) ? data.notificaciones : [];
      setNotificacionesUsuario(lista);
      setNoLeidasUsuario(Number(data?.noLeidas || 0));
      procesarNotificacionesNavegador(lista);
    } catch (err) {
      if (!silenciosa) {
        console.error(err.message || 'No se pudieron cargar notificaciones');
      }
    } finally {
      notificacionesEnVueloRef.current = false;
      if (!silenciosa) {
        setCargandoNotificaciones(false);
      }
    }
  }, [usuarioActual?.username, obtenerHeadersNotificaciones, procesarNotificacionesNavegador]);

  const sincronizarSuscripcionWebPush = useCallback(async () => {
    if (!usuarioActual?.username || !soportaWebPushNativo()) {
      return false;
    }

    try {
      const config = await obtenerWebPushPublicKey();
      if (!config?.enabled || !config?.publicKey) {
        return false;
      }

      await navigator.serviceWorker.register('/sw-notificaciones.js');
      const registration = await navigator.serviceWorker.ready;

      let subscription = await registration.pushManager.getSubscription();
      if (!subscription) {
        subscription = await registration.pushManager.subscribe({
          userVisibleOnly: true,
          applicationServerKey: urlBase64ToUint8Array(config.publicKey),
        });
      }

      await registrarWebPushSuscripcion({
        username: usuarioActual.username,
        subscription: subscription.toJSON(),
      });

      return true;
    } catch (err) {
      console.warn('No se pudo sincronizar suscripción Web Push:', err?.message || err);
      return false;
    }
  }, [usuarioActual?.username]);

  const desactivarSuscripcionWebPushLocal = useCallback(async () => {
    if (!usuarioActual?.username || !soportaWebPushNativo()) {
      return;
    }

    try {
      const registration = await navigator.serviceWorker.ready;
      const subscription = await registration.pushManager.getSubscription();

      if (subscription?.endpoint) {
        try {
          await desactivarWebPushSuscripcion({
            username: usuarioActual.username,
            endpoint: subscription.endpoint,
          });
        } catch (err) {
          console.warn('No se pudo informar baja de suscripción en backend:', err?.message || err);
        }

        await subscription.unsubscribe();
      }
    } catch (err) {
      console.warn('No se pudo desactivar suscripción Web Push local:', err?.message || err);
    }
  }, [usuarioActual?.username]);

  useEffect(() => {
    if (!notificacionesNavegadorHabilitadas) {
      setWebPushSuscrito(false);
      return;
    }

    let activa = true;
    const sincronizar = async () => {
      const ok = await sincronizarSuscripcionWebPush();
      if (activa) {
        setWebPushSuscrito(ok);
      }
    };

    sincronizar();
    return () => {
      activa = false;
    };
  }, [notificacionesNavegadorHabilitadas, sincronizarSuscripcionWebPush]);

  const toggleNotificacionesNavegador = useCallback(async () => {
    if (!usuarioActual?.username || !soportaNotificacionesNavegador()) {
      return;
    }

    const key = keyPreferenciaNotificacionesNavegador(usuarioActual.username);

    if (notificacionesNavegadorHabilitadas) {
      setNotificacionesNavegadorHabilitadas(false);
      setWebPushSuscrito(false);
      localStorage.setItem(key, 'false');
      await desactivarSuscripcionWebPushLocal();
      return;
    }

    if (Notification.permission === 'denied') {
      console.warn('Notificaciones de navegador bloqueadas por el usuario');
      localStorage.setItem(key, 'false');
      return;
    }

    if (Notification.permission !== 'granted') {
      const permiso = await Notification.requestPermission();
      if (permiso !== 'granted') {
        localStorage.setItem(key, 'false');
        return;
      }
    }

    trackingNotificacionesRef.current = {
      username: usuarioActual.username,
      initialized: false,
      seenIds: new Set(),
    };
    setNotificacionesNavegadorHabilitadas(true);
    localStorage.setItem(key, 'true');
    const suscrito = await sincronizarSuscripcionWebPush();
    setWebPushSuscrito(suscrito);
    await cargarNotificacionesUsuario({ silenciosa: true, forzar: true });
  }, [
    cargarNotificacionesUsuario,
    desactivarSuscripcionWebPushLocal,
    notificacionesNavegadorHabilitadas,
    sincronizarSuscripcionWebPush,
    usuarioActual?.username,
  ]);

  const abrirCentroNotificaciones = async () => {
    setNotificacionesAbiertas(true);
    await cargarNotificacionesUsuario({ forzar: true });
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

    const cargarSiVisible = () => {
      if (typeof document !== 'undefined' && document.visibilityState === 'hidden') {
        return;
      }
      cargarNotificacionesUsuario({ silenciosa: true });
    };

    cargarSiVisible();

    const intervalo = setInterval(
      cargarSiVisible,
      notificacionesAbiertas ? POLL_NOTIFICACIONES_ABIERTO_MS : POLL_NOTIFICACIONES_CERRADO_MS,
    );

    const onVisibilityChange = () => {
      if (document.visibilityState === 'visible') {
        cargarNotificacionesUsuario({ silenciosa: true });
      }
    };

    document.addEventListener('visibilitychange', onVisibilityChange);

    return () => {
      clearInterval(intervalo);
      document.removeEventListener('visibilitychange', onVisibilityChange);
    };
  }, [cargarNotificacionesUsuario, enPanel, usuarioActual?.username, notificacionesAbiertas]);

  useEffect(() => {
    if (!(enPanel && usuarioActual?.username)) {
      setNotificacionesAbiertas(false);
      setNotificacionesUsuario([]);
      setNoLeidasUsuario(0);
      setWebPushSuscrito(false);
      notificacionesEnVueloRef.current = false;
      ultimaCargaNotificacionesRef.current = 0;
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
        soportaNotificacionesNavegador={soportaNotificacionesNavegador()}
        notificacionesNavegadorHabilitadas={notificacionesNavegadorHabilitadas}
        onVolver={() => navigate('/')}
        onAbrirCentroNotificaciones={abrirCentroNotificaciones}
        onToggleNotificacionesNavegador={toggleNotificacionesNavegador}
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
