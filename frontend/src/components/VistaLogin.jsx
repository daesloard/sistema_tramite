import { Suspense, lazy } from 'react';
import HeaderConVolver from './HeaderConVolver';

const Login = lazy(() => import('./Login'));

const styles = {
  appLogin: { minHeight: '100vh', background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)' },
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

export default function VistaLogin({ onVolver, onLoginSuccess }) {
  return (
    <div style={styles.appLogin}>
      <HeaderConVolver onVolver={onVolver} />
      <Suspense fallback={<CargandoModulo texto="Cargando inicio de sesión..." />}>
        <Login onLoginSuccess={onLoginSuccess} />
      </Suspense>
    </div>
  );
}
