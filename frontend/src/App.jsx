import { Component, Suspense, lazy, useMemo, useCallback } from 'react';
import { Navigate, Route, Routes, useLocation, useNavigate } from 'react-router-dom';
import { formatearFechaHora as formatearFechaHoraUtil } from './utils/dateFormat';
import PanelConSesion from './components/PanelConSesion';
import CentroNotificacionesModal from './components/CentroNotificacionesModal';
import VistaInicio from './components/VistaInicio';
import VistaLogin from './components/VistaLogin';
import VistaRadicar from './components/VistaRadicar';
import VistaVerificar from './components/VistaVerificar';
import { getAppStyles } from './styles/components/AppStyles';
import { useAuth } from './hooks/useAuth';
import { useNotificaciones, soportaNotificacionesNavegador } from './hooks/useNotificaciones';

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

const rutaPanelPorRol = (rol) => RUTA_PANEL_POR_ROL[rol] || null;
const esRutaInterna = (ruta) => typeof ruta === 'string' && ruta.startsWith('/');

export default function App() {
  const navigate = useNavigate();
  const location = useLocation();

  const {
    sesionHidratada,
    usuarioActual,
    cerrarSesion,
    cambiarUsuario,
    handleLoginUnificado,
  } = useAuth(navigate, location, rutaPanelPorRol, esRutaInterna);

  const enPanel = location.pathname.startsWith('/panel');

  const {
    notificacionesUsuario,
    noLeidasUsuario,
    notificacionesAbiertas,
    cargandoNotificaciones,
    notificacionesNavegadorHabilitadas,
    abrirCentroNotificaciones,
    cerrarCentroNotificaciones,
    marcarNotificacionLeida,
    marcarTodasNotificacionesLeidas,
    toggleNotificacionesNavegador,
    limpiarNotificaciones
  } = useNotificaciones(usuarioActual, enPanel);

  const handleCerrarSesion = useCallback(() => cerrarSesion(limpiarNotificaciones), [cerrarSesion, limpiarNotificaciones]);
  const handleCambiarUsuario = useCallback(() => cambiarUsuario(limpiarNotificaciones), [cambiarUsuario, limpiarNotificaciones]);

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

  const formatearFechaHora = useCallback((valor) => formatearFechaHoraUtil(valor, {
    fallback: '-',
    incluirSegundos: true,
  }), []);

  const centroNotificaciones = useMemo(() => (
    <CentroNotificacionesModal
      abierto={notificacionesAbiertas}
      cargando={cargandoNotificaciones}
      notificaciones={notificacionesUsuario}
      onCerrar={cerrarCentroNotificaciones}
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
    cerrarCentroNotificaciones
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
        onCambiarUsuario={handleCambiarUsuario}
        onCerrarSesion={handleCerrarSesion}
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
                <PanelVerificador usuarioActual={usuarioActual} />
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
                <PanelAlcalde usuarioActual={usuarioActual} />
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
