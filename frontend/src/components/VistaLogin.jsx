import { Suspense, lazy } from 'react';
import HeaderConVolver from './HeaderConVolver';

import { getVistaLoginStyles } from '../styles/components/VistaLoginStyles';
const Login = lazy(() => import('./Login'));

const styles = getVistaLoginStyles();

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